package io.cloud_storage.service;

import io.cloud_storage.domain.dto.ResourceType;
import io.cloud_storage.domain.model.Resourse;
import io.cloud_storage.domain.response.ResourseResponseDto;
import io.cloud_storage.exceptions.InvalidPathException;
import io.cloud_storage.exceptions.ResourceAlreadyExistsException;
import io.cloud_storage.exceptions.ResourceNotFoundException;
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

        log.info("getResource called - userId={}, path='{}'", userId, path);

        String normalizedBase = validation.normalizeBase(path);
        String normalizedDir = validation.normalizeDirectory(path);

        String fileKey = userKey(userId, normalizedBase);
        String dirKey = userKey(userId, normalizedDir);

        log.debug("getResource normalizedBase='{}' fileKey='{}'", normalizedBase, fileKey);
        log.debug("getResource normalizedDir ='{}' dirKey ='{}'", normalizedDir, dirKey);

        boolean dirMarkerExists = minioRepository.checkObjectExists(dirKey);
        if (dirMarkerExists) {
            log.info("getResource DIRECTORY - marker exists dirKey='{}'", dirKey);
            return ResourseResponseDto.builder()
                    .path(extractParentPath(normalizedDir))
                    .name(extractFileName(normalizedDir))
                    .size(null)
                    .type(ResourceType.DIRECTORY)
                    .build();
        }

        try {
            StatObjectResponse stat = minioRepository.statObject(fileKey);
            log.info("getResource FILE - fileKey='{}' size={}", fileKey, stat.size());

            return ResourseResponseDto.builder()
                    .path(extractParentPath(normalizedBase))
                    .name(extractFileName(normalizedBase))
                    .size(stat.size())
                    .type(ResourceType.FILE)
                    .build();

        } catch (Exception e) {
            log.debug("getResource statObject failed for fileKey='{}' err={}", fileKey, e.toString());
        }

        List<Item> dirItems = minioRepository.listObject(dirKey, false);
        if (!dirItems.isEmpty()) {
            log.info("getResource DIRECTORY - prefix exists dirKey='{}' items={}", dirKey, dirItems.size());
            return ResourseResponseDto.builder()
                    .path(extractParentPath(normalizedDir))
                    .name(extractFileName(normalizedDir) + "/")
                    .size(null)
                    .type(ResourceType.DIRECTORY)
                    .build();
        }

        throw new ResourceNotFoundException("Resource not found: " + normalizedBase);
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

    public ResourseResponseDto createDirectory(UUID userId, String path) {
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
        return getResource(userId, normalizedDir);
    }

    public void deleteResource(UUID userId, String path) {
        if (path == null || path.isBlank()) {
            throw new InvalidPathException("Path cannot be empty");
        }

        String normalized = validation.normalizeBase(path);
        String key = userKey(userId, normalized);

        boolean isFile = true;
        try {
            minioRepository.statObject(key);
        } catch (Exception e) {
            isFile = false;
        }

        if (isFile) {
            minioRepository.deleteObject(key);
            return;
        }

        String dirKey = key.endsWith("/") ? key : key + "/";
        List<Item> items = minioRepository.listObject(dirKey, true);

        if (items.isEmpty()) {
            throw new StorageOperationException("Resource does not exist: " + normalized);
        }

        for (Item item : items) {
            String objectName = item.objectName();
            log.debug("Deleting object: {}", objectName);
            minioRepository.deleteObject(objectName);
        }
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

    public ResourseResponseDto moveResource(UUID userId, String fromPath, String toPath) {
        if (fromPath == null || fromPath.isBlank() || toPath == null || toPath.isBlank()) {
            throw new InvalidPathException("Paths cannot be empty");
        }

        String normalizedFromBase = validation.normalizeBase(fromPath);
        String normalizedToBase = validation.normalizeBase(toPath);

        String sourceKey = userKey(userId, normalizedFromBase);
        String targetKey = userKey(userId, normalizedToBase);

        boolean sourceLooksDir = fromPath.endsWith("/") || normalizedFromBase.endsWith("/") || sourceKey.endsWith("/");

        boolean isDir;
        String sourceDirKey = sourceKey.endsWith("/") ? sourceKey : sourceKey + "/";
        String targetDirKey = targetKey.endsWith("/") ? targetKey : targetKey + "/";

        if (sourceLooksDir) {
            isDir = true;
            sourceKey = sourceDirKey;
            targetKey = targetDirKey;
        } else {
            try {
                minioRepository.statObject(sourceKey);
                isDir = false;
            } catch (Exception e) {
                List<Item> probe = minioRepository.listObject(sourceDirKey, false);
                if (!probe.isEmpty()) {
                    isDir = true;
                    sourceKey = sourceDirKey;
                    targetKey = targetDirKey;
                } else {
                    throw new ResourceNotFoundException("Resource not found: " + fromPath);
                }
            }
        }

        if (minioRepository.checkObjectExists(targetKey)) {
            throw new ResourceAlreadyExistsException("Target already exists: " + toPath);
        }
        if (isDir && !minioRepository.listObject(targetKey, false).isEmpty()) {
            throw new ResourceAlreadyExistsException("Target directory already exists: " + toPath);
        }

        if (isDir) {
            List<Item> items = minioRepository.listObject(sourceKey, true);

            if (items.isEmpty()) {
                minioRepository.copyObject(sourceKey, targetKey);
                minioRepository.deleteObject(sourceKey);
            } else {
                for (Item item : items) {
                    String currentSourceKey = item.objectName();
                    if (currentSourceKey == null) continue;

                    if (!currentSourceKey.startsWith(sourceKey)) continue;

                    String relative = currentSourceKey.substring(sourceKey.length());
                    String currentTargetKey = targetKey + relative;

                    if (minioRepository.checkObjectExists(currentTargetKey)) {
                        throw new ResourceAlreadyExistsException("Target already exists: " + currentTargetKey);
                    }

                    minioRepository.copyObject(currentSourceKey, currentTargetKey);
                }

                for (Item item : items) {
                    String k = item.objectName();
                    if (k != null) {
                        minioRepository.deleteObject(k);
                    }
                }

                if (minioRepository.checkObjectExists(sourceKey)) {
                    minioRepository.deleteObject(sourceKey);
                }
            }
            return getResource(userId, toPath.endsWith("/") ? toPath : toPath + "/");

        } else {
            minioRepository.copyObject(sourceKey, targetKey);
            minioRepository.deleteObject(sourceKey);
            return getResource(userId, toPath);
        }
    }

    public List<ResourseResponseDto> listDirectory(UUID userId, String path) {
        log.info("listDirectory called - userId={}, path='{}'", userId, path);

        String normalizedDir = validation.normalizeDirectory(path);
        String dirKey = userKey(userId, normalizedDir);
        log.debug("listDirectory normalizedDir='{}' dirKey='{}'", normalizedDir, dirKey);

        List<Item> items = minioRepository.listObject(dirKey, false);
        log.info("listDirectory minio returned {} items for dirKey='{}'", items.size(), dirKey);

        List<ResourseResponseDto> results = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            String objectName = item.objectName();

            if (objectName == null) {
                log.debug("listDirectory skip item[{}]: objectName is null", i);
                continue;
            }

            if (objectName.equals(dirKey)) {
                log.debug("listDirectory skip self marker item[{}]: '{}'", i, objectName);
                continue;
            }

            String relative = objectName.startsWith(dirKey)
                    ? objectName.substring(dirKey.length())
                    : objectName;

            if (relative.isEmpty()) {
                log.debug("listDirectory skip item[{}]: empty relative for '{}'", i, objectName);
                continue;
            }

            boolean isDirectory = relative.endsWith("/") || objectName.endsWith("/");
            String displayName = isDirectory ? relative.substring(0, relative.length() - 1) : relative;

            if (displayName.isBlank()) {
                log.debug("listDirectory skip item[{}]: blank displayName for '{}'", i, objectName);
                continue;
            }

            String nameForClient = isDirectory ? (displayName + "/") : displayName;

            log.debug("listDirectory item[{}]: objectName='{}', relative='{}', isDir={}, name='{}', size={}",
                    i, objectName, relative, isDirectory, nameForClient, isDirectory ? null : item.size());

            results.add(ResourseResponseDto.builder()
                    .path(normalizedDir)
                    .name(nameForClient)
                    .size(isDirectory ? null : item.size())
                    .type(isDirectory ? ResourceType.DIRECTORY : ResourceType.FILE)
                    .build());
        }

        log.info("listDirectory returning {} items for normalizedDir='{}'", results.size(), normalizedDir);
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

    private String userKey(UUID userId, String path) {
        String key = userId + "/" + path;
        log.trace("Generated user key: '{}' for userId: {}, path: '{}'", key, userId, path);
        return key;
    }

    private String extractFileName(String fullObjectName) {
        if (fullObjectName == null || fullObjectName.isEmpty()) {
            log.warn("extractFileName received null or empty input");
            return "";
        }

        String cleanPath = fullObjectName.endsWith("/")
                ? fullObjectName.substring(0, fullObjectName.length() - 1)
                : fullObjectName;

        int lastSlash = cleanPath.lastIndexOf('/');
        String result = (lastSlash == -1) ? cleanPath : cleanPath.substring(lastSlash + 1);

        log.debug("extractFileName: input='{}', output='{}'", fullObjectName, result);
        return result;
    }

    private String extractParentPath(String path) {
        if (path == null || path.isEmpty()) {
            log.warn("extractParentPath received null or empty input");
            return "";
        }

        String cleanPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = cleanPath.lastIndexOf('/');
        String result = (lastSlash == -1) ? "" : cleanPath.substring(0, lastSlash + 1);

        log.debug("extractParentPath: input='{}', output='{}'", path, result);
        return result;
    }
}
