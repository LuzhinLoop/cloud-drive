package io.cloud_storage.controller;

import io.cloud_storage.domain.response.ResourseResponseDto;
import io.cloud_storage.security.DriveUserDetails;
import io.cloud_storage.service.MinioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/directory")
@RequiredArgsConstructor
public class DirectoryController {

    private final MinioService minioService;

    @GetMapping
    public List<ResourseResponseDto> listDirectory(
            @RequestParam(name = "path")
            String path,
            @AuthenticationPrincipal DriveUserDetails principal
    ) {
        return minioService.listDirectory(principal.getId(), path);
    }

    @PostMapping
    public ResponseEntity<ResourseResponseDto> createDirectory(
            @Valid
            @RequestParam(name = "path")
            @NotBlank(message = "Parameter \"path\" must not be blank") String path,
            @AuthenticationPrincipal DriveUserDetails principal
    ) {
        ResourseResponseDto created = minioService.createDirectory(principal.getId(), path);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
