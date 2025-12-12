package io.cloud_storage.exceptions;

public class ResourceNotFoundException extends StorageException {

    public ResourceNotFoundException(String path) {
        super("Resource not found: " + path);
    }

    public ResourceNotFoundException(String path, Throwable cause) {
        super("Resource not found: " + path, cause);
    }
}