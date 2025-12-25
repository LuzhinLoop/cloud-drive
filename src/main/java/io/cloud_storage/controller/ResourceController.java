package io.cloud_storage.controller;

import io.cloud_storage.domain.model.Resourse;
import io.cloud_storage.domain.response.ResourseResponseDto;
import io.cloud_storage.security.DriveUserDetails;
import io.cloud_storage.service.MinioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/resource")
@RequiredArgsConstructor
public class ResourceController {

    private final MinioService minioService;

    @GetMapping
    public ResourseResponseDto getResource(
            @Valid
            @RequestParam(name = "path")
            @NotBlank(message = "Parameter \"path\" must not be blank") String path,
            @AuthenticationPrincipal DriveUserDetails principal
    ) {
        return minioService.getResource(principal.getId(), path);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteResource(
            @Valid
            @RequestParam(name = "path")
            @NotBlank(message = "Parameter \"path\" must not be blank") String path,
            @AuthenticationPrincipal DriveUserDetails principal
    ) {

        minioService.deleteResource(principal.getId(), path);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(
            @Valid
            @RequestParam(name = "path")
            @NotBlank(message = "Parameter \"path\" must not be blank") String path,
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
            @Valid
            @RequestParam(name = "from")
            @NotBlank(message = "Parameter \"from\" must not be blank") String from,
            @Valid
            @RequestParam(name = "to")
            @NotBlank(message = "Parameter \"to\" must not be blank") String to,
            @AuthenticationPrincipal DriveUserDetails principal) {

        return minioService.moveResource(principal.getId(), from, to);
    }

    @GetMapping("/search")
    public List<ResourseResponseDto> search(
            @Valid
            @RequestParam(name = "query")
            @NotBlank(message = "Parameter \"query\" must not be blank")
            String query,
            @AuthenticationPrincipal DriveUserDetails principal) {

        return minioService.searchResources(principal.getId(), query);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ResourseResponseDto>> upload(
            @RequestParam("path") String path,
            @RequestParam("object") List<MultipartFile> files,
            @AuthenticationPrincipal DriveUserDetails principal
    ) {
        List<ResourseResponseDto> uploaded =
                minioService.upload(principal.getId(), path, files);

        return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
    }
}
