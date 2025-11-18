package io.cloud_storage.domain.response;

import jakarta.validation.constraints.Size;

public record UserResponseDto(
        @Size(min = 5, max = 25) String username
) {
}
