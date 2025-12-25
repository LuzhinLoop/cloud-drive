package io.cloud_storage.controller;

import io.cloud_storage.domain.model.Resourse;
import io.cloud_storage.domain.response.ResourseResponseDto;
import io.cloud_storage.security.DriveUserDetails;
import io.cloud_storage.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/resource")
@RequiredArgsConstructor
public class ResourceController {

    private final MinioService minioService;

    @GetMapping
    public ResourseResponseDto getResource(
            @RequestParam String path,
            @AuthenticationPrincipal DriveUserDetails principal
    ) {
        return minioService.getResource(principal.getId(), path);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteResource(
            @RequestParam String path,
            @AuthenticationPrincipal DriveUserDetails principal
    ) {
        minioService.deleteResource(principal.getId(), path);
        return ResponseEntity.noContent().build(); // 204
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestParam String path,
            @AuthenticationPrincipal DriveUserDetails principal
    ) {
        Resourse resource =
                minioService.downloadResource(principal.getId(), path);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.name() + "\""
                )
                .body(new InputStreamResource(resource.inputStream()));
    }

    @GetMapping("/move")
    public ResourseResponseDto move(
            @RequestParam String from,
            @RequestParam String to,
            @AuthenticationPrincipal DriveUserDetails principal
    ) {
        return minioService.moveResource(principal.getId(), from, to);
    }

    @GetMapping("/search")
    public List<ResourseResponseDto> search(
            @RequestParam String query,
            @AuthenticationPrincipal DriveUserDetails principal
    ) {
        return minioService.searchResources(principal.getId(), query);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ResourseResponseDto>> upload(
            @RequestParam String path,
            @RequestPart("file") List<MultipartFile> files,
            @AuthenticationPrincipal DriveUserDetails principal
    ) {
        List<ResourseResponseDto> uploaded =
                minioService.upload(principal.getId(), path, files);

        return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
    }
}
