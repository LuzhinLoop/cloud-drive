package io.cloud_storage.service;

import io.cloud_storage.domain.dto.ResourceType;
import io.cloud_storage.domain.model.Resourse;
import io.cloud_storage.domain.response.ResourseResponseDto;
import io.cloud_storage.exceptions.InvalidPathException;
import io.cloud_storage.exceptions.ResourceAlreadyExistsException;
import io.cloud_storage.exceptions.StorageOperationException;
import io.cloud_storage.repository.MinioRepository;
import io.cloud_storage.util.PathValidation;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioRepository minioRepository;
    private final PathValidation validation;

    public ResourseResponseDto getResource(UUID userId, String path) {
        if (path == null || path.isBlank()) {
            throw new InvalidPathException("Path cannot be empty");
        }

        String normalizedPath = validation.normalizeBase(path);
        if (path.endsWith("/") && !normalizedPath.endsWith("/")) {
            normalizedPath += "/";
        }

        String key = userKey(userId, normalizedPath);
        StatObjectResponse stat = minioRepository.statObject(key);
        boolean isDirectory = normalizedPath.endsWith("/");

        return ResourseResponseDto.builder()
                .path(extractParentPath(normalizedPath))
                .name(extractFileName(normalizedPath))
                .size(isDirectory ? null : stat.size())
                .type(isDirectory ? ResourceType.DIRECTORY : ResourceType.FILE)
                .build();
    }

    public List<ResourseResponseDto> upload(UUID userId, String path, List<MultipartFile> files) {
        String baseDir = validation.normalizeDirectory(path);

        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<FilePathInfo> filePaths = new ArrayList<>();
        Set<String> allDirectories = new HashSet<>();

        for (MultipartFile file : files) {
            if (file == null) continue;

            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) continue;

            String normalizedFilePath = validation.normalizeFilePath(originalName);
            String relativePath = baseDir + normalizedFilePath;
            String fileKey = userKey(userId, relativePath);

            if (minioRepository.checkObjectExists(fileKey)) {
                throw new ResourceAlreadyExistsException("File already exists: " + relativePath);
            }

            extractDirectories(relativePath, allDirectories);
            filePaths.add(new FilePathInfo(file, relativePath, fileKey));
        }

        createAllDirectories(userId, allDirectories);

        List<ResourseResponseDto> responses = new ArrayList<>();

        for (FilePathInfo info : filePaths) {
            try (InputStream inputStream = info.file.getInputStream()) {
                minioRepository.uploadObject(info.fileKey, inputStream, info.file.getSize());

                responses.add(ResourseResponseDto.builder()
                        .path(extractParentPath(info.relativePath))
                        .name(extractFileName(info.relativePath))
                        .size(info.file.getSize())
                        .type(ResourceType.FILE)
                        .build());

            } catch (IOException e) {
                throw new StorageOperationException(
                        "Failed to upload file: " + info.file.getOriginalFilename(), e
                );
            }
        }

        return responses;
    }

    public void createDirectory(UUID userId, String path) {
        String normalizedDir = validation.normalizeDirectory(path);
        if (normalizedDir.isEmpty()) {
            throw new InvalidPathException("Root directory cannot be created explicitly");
        }

        String dirKey = userKey(userId, normalizedDir);

        if (minioRepository.checkObjectExists(dirKey)) {
            throw new ResourceAlreadyExistsException("Directory already exists: " + normalizedDir);
        }

        Set<String> allDirectories = new HashSet<>();
        extractDirectories(normalizedDir, allDirectories);

        createAllDirectories(userId, allDirectories);
    }

    public void deleteResource(UUID userId, String path) {
        if (path == null || path.isBlank()) {
            throw new InvalidPathException("Path cannot be empty");
        }

        boolean isDirectory = path.endsWith("/");
        String normalized = validation.normalizeBase(path);

        if (isDirectory && !normalized.endsWith("/")) {
            normalized += "/";
        }

        String key = userKey(userId, normalized);
        minioRepository.statObject(key);

        if (isDirectory) {
            List<Item> items = minioRepository.listObject(key, true);
            for (Item item : items) {
                minioRepository.deleteObject(item.objectName());
            }
        }

        minioRepository.deleteObject(key);
    }

    public Resourse downloadResource(UUID userId, String path) {
        if (path == null || path.isBlank()) {
            throw new InvalidPathException("Path cannot be empty");
        }

        boolean isDirectory = path.endsWith("/");
        String normalized = validation.normalizeBase(path);

        if (isDirectory && !normalized.endsWith("/")) {
            normalized += "/";
        }

        String key = userKey(userId, normalized);
        minioRepository.statObject(key);

        if (isDirectory) {
            return downloadDirectoryAsZip(userId, normalized);
        } else {
            StatObjectResponse stat = minioRepository.statObject(key);
            InputStream stream = minioRepository.downloadObject(key);

            return new Resourse(
                    stream,
                    extractFileName(normalized),
                    ResourceType.FILE,
                    stat.size()
            );
        }
    }

    public ResourseResponseDto moveResource(UUID userId, String from, String to) {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new InvalidPathException("Paths cannot be empty");
        }

        boolean isDirectory = from.endsWith("/");

        String normalizedFrom = validation.normalizeBase(from);
        String normalizedTo = validation.normalizeBase(to);

        if (isDirectory) {
            if (!normalizedFrom.endsWith("/")) normalizedFrom += "/";
            if (!normalizedTo.endsWith("/")) normalizedTo += "/";
        }

        String fromKey = userKey(userId, normalizedFrom);
        String toKey = userKey(userId, normalizedTo);

        minioRepository.statObject(fromKey);

        if (minioRepository.checkObjectExists(toKey)) {
            throw new ResourceAlreadyExistsException("Resource already exists: " + to);
        }

        if (isDirectory) {
            moveDirectory(userId, normalizedFrom, normalizedTo);
        } else {
            moveFile(userId, fromKey, toKey, normalizedTo);
        }

        StatObjectResponse stat = minioRepository.statObject(toKey);

        return ResourseResponseDto.builder()
                .path(extractParentPath(normalizedTo))
                .name(extractFileName(normalizedTo))
                .size(isDirectory ? null : stat.size())
                .type(isDirectory ? ResourceType.DIRECTORY : ResourceType.FILE)
                .build();
    }

    private void moveFile(UUID userId, String fromKey, String toKey, String normalizedTo) {
        Set<String> directories = new HashSet<>();
        extractDirectories(normalizedTo, directories);
        createAllDirectories(userId, directories);

        StatObjectResponse stat = minioRepository.statObject(fromKey);

        try (InputStream fileStream = minioRepository.downloadObject(fromKey)) {
            minioRepository.uploadObject(toKey, fileStream, stat.size());
        } catch (Exception e) {
            throw new StorageOperationException("Failed to move file", e);
        }

        minioRepository.deleteObject(fromKey);
    }

    private void moveDirectory(UUID userId, String fromDir, String toDir) {
        String fromKey = userKey(userId, fromDir);
        String toKey = userKey(userId, toDir);

        List<Item> items = minioRepository.listObject(fromKey, true);

        Set<String> directories = new HashSet<>();
        extractDirectories(toDir, directories);
        createAllDirectories(userId, directories);

        for (Item item : items) {
            String oldObjectName = item.objectName();
            String relativePath = oldObjectName.substring(fromKey.length());
            String newObjectName = toKey + relativePath;

            long size = item.size();

            try (InputStream stream = minioRepository.downloadObject(oldObjectName)) {
                minioRepository.uploadObject(newObjectName, stream, size);
            } catch (Exception e) {
                throw new StorageOperationException("Failed to move directory item", e);
            }

            minioRepository.deleteObject(oldObjectName);
        }
    }

    public List<ResourseResponseDto> searchResources(UUID userId, String query) {
        if (query == null || query.isBlank()) {
            throw new InvalidPathException("Query cannot be empty");
        }

        String userPrefix = userKey(userId, "");
        List<Item> items = minioRepository.listObject(userPrefix, true);

        List<ResourseResponseDto> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        for (Item item : items) {
            String objectName = item.objectName();
            String relativePath = objectName.substring(userPrefix.length());

            String name = extractFileName(relativePath);

            if (name.toLowerCase().contains(lowerQuery)) {
                boolean isDirectory = relativePath.endsWith("/");

                results.add(ResourseResponseDto.builder()
                        .path(extractParentPath(relativePath))
                        .name(name)
                        .size(isDirectory ? null : item.size())
                        .type(isDirectory ? ResourceType.DIRECTORY : ResourceType.FILE)
                        .build());
            }
        }

        return results;
    }

    public List<ResourseResponseDto> listDirectory(UUID userId, String path) {
        String normalizedDir = validation.normalizeDirectory(path);
        String dirKey = userKey(userId, normalizedDir);

        minioRepository.statObject(dirKey);

        List<Item> items = minioRepository.listObject(dirKey, false);
        List<ResourseResponseDto> results = new ArrayList<>();

        for (Item item : items) {
            String objectName = item.objectName();

            if (objectName.equals(dirKey)) {
                continue;
            }

            String relativePath = objectName.substring(userKey(userId, "").length());
            boolean isDirectory = relativePath.endsWith("/");

            results.add(ResourseResponseDto.builder()
                    .path(extractParentPath(relativePath))
                    .name(extractFileName(relativePath))
                    .size(isDirectory ? null : item.size())
                    .type(isDirectory ? ResourceType.DIRECTORY : ResourceType.FILE)
                    .build());
        }

        return results;
    }

    private Resourse downloadDirectoryAsZip(UUID userId, String dirPath) {
        String dirKey = userKey(userId, dirPath);
        List<Item> items = minioRepository.listObject(dirKey, true);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);

            for (Item item : items) {
                String objectName = item.objectName();

                if (objectName.endsWith("/")) {
                    continue;
                }

                String relativePath = objectName.substring(dirKey.length());

                ZipEntry entry = new ZipEntry(relativePath);
                zos.putNextEntry(entry);

                try (InputStream fileStream = minioRepository.downloadObject(objectName)) {
                    fileStream.transferTo(zos);
                }
                zos.closeEntry();
            }

            zos.close();

            String zipName = extractFileName(dirPath) + ".zip";

            return new Resourse(
                    new ByteArrayInputStream(baos.toByteArray()),
                    zipName,
                    ResourceType.FILE,
                    baos.size()
            );

        } catch (IOException e) {
            throw new StorageOperationException("Failed to create ZIP archive", e);
        }
    }

    private void extractDirectories(String path, Set<String> directories) {
        int lastSlash = 0;
        while ((lastSlash = path.indexOf('/', lastSlash + 1)) != -1) {
            directories.add(path.substring(0, lastSlash + 1));
        }
        if (path.endsWith("/")) {
            directories.add(path);
        }
    }

    private void createAllDirectories(UUID userId, Set<String> directories) {
        for (String directory : directories) {
            String dirKey = userKey(userId, directory);

            if (!minioRepository.checkObjectExists(dirKey)) {
                minioRepository.uploadObject(dirKey, InputStream.nullInputStream(), 0);
            }
        }
    }

    private record FilePathInfo(
            MultipartFile file,
            String relativePath,
            String fileKey
    ) {
    }

    private String userKey(UUID id, String path) {
        return "user-%s/resource/".formatted(id) + path;
    }

    private String extractFileName(String path) {
        if (path == null || path.isEmpty()) return "";
        String cleanPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = cleanPath.lastIndexOf('/');
        if (lastSlash == -1) return cleanPath;
        return cleanPath.substring(lastSlash + 1);
    }

    private String extractParentPath(String path) {
        if (path == null || path.isEmpty()) return "";
        String cleanPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = cleanPath.lastIndexOf('/');
        if (lastSlash == -1) return "";
        return cleanPath.substring(0, lastSlash + 1);
    }
}