package io.cloud_storage.exceptions;

public class InvalidPathException extends StorageException {

    public InvalidPathException(String message) {
        super("Invalid path: " + message);
    }

    public InvalidPathException(String path, String reason) {
        super("Invalid path '" + path + "': " + reason);
    }
}
