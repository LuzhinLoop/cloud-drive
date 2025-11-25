package io.cloud_storage.repository;

import io.cloud_storage.Exeptions.S3RepositoryException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MinioRepository implements S3Repository {

    private final MinioClient minioClient;

    private static final long PART_SIZE = 10485760L;

    @Value("${minio.bucket-name}")
    private String bucketName;

    private boolean bucketExists() {
        try {
            return minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
        } catch (Exception e) {
            log.error("Failed to check if bucket exists: {}", bucketName, e);
            throw new RuntimeException("Failed to check if bucket exists. Bucket name: " + bucketName, e);
        }
    }

    @Override
    public void createBucketIfNotExists() {
        try {
            if (bucketExists()) {
                return;
            }
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
            log.info("Bucket created: {}", bucketName);
        } catch (Exception e) {
            log.error("Failed to create bucket: {}", bucketName, e);
            throw new RuntimeException("Failed to create bucket: " + bucketName, e);
        }
    }

    @Override
    public boolean objectExist(String path) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                log.debug("Object not found: {}", path);
                return false;
            }
            log.error("Minio error for path {}: {}", path, e.getMessage());
            throw new S3RepositoryException(e);
        } catch (Exception e) {
            log.error("System error checking object: {}", path, e);
            throw new S3RepositoryException(e);
        }
    }

    @Override
    public void saveObject(String path, String contentType, InputStream inputStream) {
        try {
            minioClient.putObject(PutObjectArgs
                    .builder()
                    .bucket(bucketName)
                    .object(path)
                    .stream(inputStream, -1, PART_SIZE)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("Failed to save object: {}", path, e);
            throw new S3RepositoryException(e);
        }
    }

    @Override
    public InputStream getObject(String path) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Item> listUserFiles(String prefix) {

        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/"; // или перенести в сервис

        try {
            List<Item> response = new ArrayList<>();

            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(normalizedPrefix)
                            .recursive(false)
                            .build()
            );

            for (Result<Item> result : results) {
                response.add(result.get());
            }
            return response;
        } catch (Exception e) {
            log.error("Error listing object for path: {}", prefix, e);
            throw new S3RepositoryException(e);
        }
    }

    public void copyObject(String sourcePath, String destinationPath) {
        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucketName)
                            .source(CopySource.builder().bucket(bucketName).object(sourcePath).build())
                            .object(destinationPath)
                            .build());
        } catch (Exception e) {
            log.error("Error copying object from {} to {}", sourcePath, destinationPath, e);
            throw new S3RepositoryException(e);
        }
    }

    @Override
    public void removeObjectOrDirectory(String path) {
        try {
            if (path.endsWith("/")) {
                deleteByPrefix(path);
            } else {
                deleteSingleObject(path);
            }
        } catch (Exception e) {
            log.error("Failed to execute removeObjectOrDirectory for path: {}", path, e);
            throw new S3RepositoryException(e);
        }
    }

    private void deleteByPrefix(String prefix) {

        try {
            Iterable<Result<Item>> results = minioClient
                    .listObjects(
                            ListObjectsArgs.builder()
                                    .bucket(bucketName)
                                    .prefix(prefix)
                                    .recursive(true)
                                    .build()
                    );

            List<DeleteObject> objectsToDelete = new ArrayList<>();

            for (Result<Item> result : results) {
                objectsToDelete.add(new DeleteObject(result.get().objectName()));
            }

            if (objectsToDelete.isEmpty()) {
                log.info("No objects found for prefix {}, nothing to delete", prefix);
                return;
            }

            Iterable<Result<DeleteError>> deleteResult = minioClient.removeObjects(
                    RemoveObjectsArgs.builder()
                            .bucket(bucketName)
                            .objects(objectsToDelete)
                            .build()
            );

            for (Result<DeleteError> deleteErrorResult : deleteResult) {
                DeleteError error = deleteErrorResult.get();
                log.error("Failed to delete object {}: {}", error.objectName(), error.message());
            }
            log.info("Completed bulk deletion for prefix {}", prefix);

        } catch (Exception e) {
            throw new S3RepositoryException(e);
        }
    }

    private void deleteSingleObject(String path) {

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to delete object: {}", path, e);
            throw new S3RepositoryException(e);
        }
    }
}
