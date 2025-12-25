package io.cloud_storage.controller;

import io.cloud_storage.domain.dto.MessageDto;
import io.cloud_storage.exceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // === 400 ===

    @ExceptionHandler(InvalidPathException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public MessageDto handleInvalidPath(InvalidPathException e) {
        return new MessageDto(e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> handleMissingPart(MissingServletRequestPartException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "The file was not uploaded");
        response.put("details", "The 'file' parameter was expected");

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public MessageDto handleBadRequestBody(HttpMessageNotReadableException e) {
        return new MessageDto("Invalid request body");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public MessageDto handleValidation(MethodArgumentNotValidException e) {
        return new MessageDto("Invalid request body");
    }

    /// === 401 ===

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public MessageDto handleBadCredentials(BadCredentialsException e) {
        return new MessageDto("Invalid credentials");
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public MessageDto handleAuth(AuthenticationException e) {
        return new MessageDto("Unauthorized");
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
    public ResponseEntity<?> handleException(Exception e) {
        e.printStackTrace();
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(e.getMessage());
    }
}
