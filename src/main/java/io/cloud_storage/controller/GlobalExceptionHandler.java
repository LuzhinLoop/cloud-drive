package io.cloud_storage.controller;

import io.cloud_storage.domain.dto.MessageDto;
import io.cloud_storage.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // === 400 ===

    @ExceptionHandler(InvalidPathException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public MessageDto handleInvalidPath(InvalidPathException e) {
        return new MessageDto(e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public MessageDto handleMissingRequestParam(MissingServletRequestParameterException e) {
        if ("path".equals(e.getParameterName())) {
            return new MessageDto("Invalid path: parameter 'path' is missing");
        }
        return new MessageDto("Missing parameter: " + e.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public MessageDto handleBadRequestBody(HttpMessageNotReadableException e) {
        return new MessageDto("Invalid request body");
    }

    /// === 401 ===

    @ExceptionHandler({ BadCredentialsException.class, AuthenticationException.class })
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public MessageDto handleAuth(AuthenticationException e) {
        return new MessageDto("Invalid credentials");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public MessageDto handleIllegalArgument(IllegalArgumentException e) {
        return new MessageDto(e.getMessage());
    }

    /// === 404 ===

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public MessageDto handleNotFound(ResourceNotFoundException e) {
        return new MessageDto(e.getMessage());
    }

    /// === 409 ===

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public MessageDto handleAlreadyExists(ResourceAlreadyExistsException e) {
        return new MessageDto(e.getMessage());
    }

    /// === 500 ===

    @ExceptionHandler(StorageOperationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public MessageDto handleStorageOperation(StorageOperationException e) {
        return new MessageDto(e.getMessage());
    }

    @ExceptionHandler(StorageException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public MessageDto handleStorage(StorageException e) {
        return new MessageDto(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public MessageDto handleUnknown(Exception e) {
        return new MessageDto("Unknown error");
    }
}
