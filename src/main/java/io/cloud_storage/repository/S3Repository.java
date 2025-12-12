package io.cloud_storage.repository;

import io.minio.StatObjectResponse;
import io.minio.messages.Item;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface S3Repository {

    void createBucketIfNotExists();

    Optional<StatObjectResponse> getObject(String path);

    void deleteObject(String path);

    InputStream downloadObject(String path);

    List<Item> listObject(String prefix, boolean recursive);

    void uploadObject(String path, InputStream stream, long size, String contentType);

}
