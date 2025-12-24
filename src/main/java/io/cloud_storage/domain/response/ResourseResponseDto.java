package io.cloud_storage.domain.response;

import io.cloud_storage.domain.dto.ResourceType;
import lombok.Builder;

@Builder
public record ResourseResponseDto (
        String path,
        String name,
        Long size,
        ResourceType type
) {}