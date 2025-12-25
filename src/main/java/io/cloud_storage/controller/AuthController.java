package io.cloud_storage.controller;

import io.cloud_storage.domain.request.UserRequestDto;
import io.cloud_storage.domain.response.UserResponseDto;
import io.cloud_storage.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Registration, authentication, and logout")
@Validated
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/sign-up")
    public ResponseEntity<UserResponseDto> registerUser(
            @Valid @RequestBody UserRequestDto request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        authService.createUser(request);

        UserResponseDto responseDto = authService.login(
                request.username(),
                request.password(),
                httpServletRequest,
                httpServletResponse
        );

        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }

    @PostMapping("/sign-in")
    public ResponseEntity<UserResponseDto> login(
            @Valid @RequestBody UserRequestDto request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        UserResponseDto response = authService.login(
                request.username(),
                request.password(),
                httpServletRequest,
                httpServletResponse
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sign-out")
    public ResponseEntity<Void> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        return ResponseEntity.noContent().build();
    }
}
