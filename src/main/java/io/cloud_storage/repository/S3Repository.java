package io.cloud_storage.repository;

import io.minio.StatObjectResponse;
import io.minio.messages.Item;

import java.io.InputStream;
import java.util.List;

public interface S3Repository {

    void createBucketIfNotExists();

    StatObjectResponse statObject(String path);

    void deleteObject(String path);

    InputStream downloadObject(String path);

    List<Item> listObject(String prefix, boolean recursive);

    void uploadObject(String path, InputStream stream, long size);

}
