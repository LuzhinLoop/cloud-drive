package io.cloud_storage.controller;

import io.cloud_storage.domain.response.UserResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(path = "api/user")
@RequiredArgsConstructor
public class UserController {

    @GetMapping("/me")
    public UserResponseDto getMe(Authentication authentication) {
        return new UserResponseDto(authentication.getName());
    }
}
