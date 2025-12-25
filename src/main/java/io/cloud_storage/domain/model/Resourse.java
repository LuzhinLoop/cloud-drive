package io.cloud_storage.domain.model;


import io.cloud_storage.domain.dto.ResourceType;

import java.io.InputStream;

public record Resourse(
        InputStream inputStream,
        String name,
        ResourceType type,
        long size
){
}
