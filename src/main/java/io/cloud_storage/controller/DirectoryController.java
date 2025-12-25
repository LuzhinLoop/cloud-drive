package io.cloud_storage.controller;

import io.cloud_storage.domain.response.ResourseResponseDto;
import io.cloud_storage.security.DriveUserDetails;
import io.cloud_storage.service.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/directory")
@RequiredArgsConstructor
public class DirectoryController {

    private final MinioService minioService;

    @GetMapping
    public List<ResourseResponseDto> listDirectory(
            @RequestParam String path,
            @AuthenticationPrincipal DriveUserDetails principal
    ) {
        return minioService.listDirectory(principal.getId(), path);
    }

    @PostMapping
    public ResponseEntity<ResourseResponseDto> createDirectory(
            @RequestParam String path,
            @AuthenticationPrincipal DriveUserDetails principal
    ) {
        ResourseResponseDto created = minioService.getResource(principal.getId(), path);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
