package io.cloud_storage.repository;

import io.cloud_storage.exceptions.StorageException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MinioRepository implements S3Repository {

    private final MinioClient minioClient;

    private static final long PART_SIZE = 10485760L; // 10 Mb

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
    public Optional<StatObjectResponse> getObject(String path) {

        try {
            StatObjectResponse response = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
            return Optional.of(response);

        } catch (Exception e) {
            if (e instanceof ErrorResponseException minioEx &&
                    "NoSuchKey".equals(minioEx.errorResponse().code())) {
                log.debug("Object not found at path: {}", path);
                return Optional.empty();
            }
            throw processException(path, e);
        }
    }

    @Override
    public InputStream downloadObject(String path) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
        } catch (Exception e) {
            throw processException(path, e);
        }
    }

    @Override
    public void uploadObject(String path, InputStream stream,
                             long size, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .stream(stream, size, PART_SIZE)
                            .build()
            );
        } catch (Exception e) {
            throw processException(path, e);
        }
    }

    @Override
    public List<Item> listObject(String prefix, boolean recursive) {
        try {
            List<Item> items = new ArrayList<>();

            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(recursive)
                            .build()
            );

            for (Result<Item> result : results) {
                items.add(result.get());
            }

            return items;
        } catch (Exception e) {
            throw processException(prefix, e);
        }
    }


    @Override
    public void deleteObject(String path) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
        } catch (Exception e) {
            throw processException(path, e);
        }
    }

    private boolean checkObjectExist(String path) {
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
                return false;
            }

            log.error("Minio error while check object for path: {}", path, e);
            throw new StorageException("Minio error while check object for path: " + path, e);
        } catch (Exception e) {
            log.error("Failed to check object: {}", path, e);
            throw new StorageException("Failed to check object: " + path, e);
        }
    }

    private StorageException processException(String path, Exception e) {

        if (e instanceof ErrorResponseException minioEx) {
            log.error("Minio error for path {}: {} - {}", path, minioEx.errorResponse().code(), minioEx.errorResponse().message());
        } else {
            log.error("Unexpected error for path {}: {}", path, e.getMessage(), e);
        }

        return new StorageException("Operation failed for path: " + path, e);
    }


}
