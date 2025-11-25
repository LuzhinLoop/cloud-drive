package io.cloud_storage.repository;

import io.minio.messages.Item;

import java.io.InputStream;
import java.util.List;

public interface S3Repository {

    void createBucketIfNotExists();

    boolean objectExist(String path);
    InputStream getObject(String path);
    void saveObject(String path, String contentType, InputStream inputStream);
    public void copyObject(String sourcePath, String destinationPath);

    List<Item> listUserFiles(String path);
    void removeObjectOrDirectory(String path);


//    void createDirectory(String bucketName, String path);


}
