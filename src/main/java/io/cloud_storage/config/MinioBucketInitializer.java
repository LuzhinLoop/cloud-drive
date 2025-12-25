package io.cloud_storage.config;

import io.cloud_storage.repository.MinioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioBucketInitializer implements ApplicationRunner {

    private final MinioRepository minioRepository;

    @Override
    public void run(ApplicationArguments args) {
        minioRepository.createBucketIfNotExists();
        log.info("MinIO bucket ensured on startup");
    }
}
