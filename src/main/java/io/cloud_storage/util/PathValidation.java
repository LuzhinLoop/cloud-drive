package io.cloud_storage.util;

import io.cloud_storage.exceptions.InvalidPathException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PathValidation {

    public String normalizeDirectory(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }

        String normalized = normalizeBase(path);

        if (!normalized.isEmpty() && !normalized.endsWith("/")) {
            normalized += "/";
        }

        return normalized;
    }

    public String normalizeFilePath(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidPathException("File name cannot be empty");
        }

        String normalized = normalizeBase(fileName);

        if (normalized.endsWith("/")) {
            throw new InvalidPathException("File name must not end with '/'");
        }

        return normalized;
    }

    public String normalizeBase(String input) {
        String normalized = input.replace("\\", "/");

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }

        if (normalized.indexOf('\0') >= 0) {
            throw new InvalidPathException("NUL character is not allowed in path");
        }

        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new InvalidPathException("Directory name '..' is not allowed");
            }
        }

        return normalized;
    }
}
