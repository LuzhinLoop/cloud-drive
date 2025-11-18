package io.cloud_storage.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequestDto(
        @NotBlank @Size(min = 5, max = 25) String username,
        @NotBlank @Size(min = 8, max = 100) String password
) {
    public UserRequestDto {
        if (username != null) {
            username = username.trim();
        }
        if (password != null) {
            password = password.trim();
        }
    }
}