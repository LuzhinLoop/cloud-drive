package io.cloud_storage.exceptions;

public class ResourceAlreadyExistsException extends StorageException {

    public ResourceAlreadyExistsException(String path) {
        super("Resource already exists: " + path);
    }
}
