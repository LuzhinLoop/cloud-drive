package io.cloud_storage.service;

import io.cloud_storage.domain.model.User;
import io.cloud_storage.domain.request.UserRequestDto;
import io.cloud_storage.domain.response.UserResponseDto;
import io.cloud_storage.mappers.UserMapper;
import io.cloud_storage.repository.UserRepository;
import io.cloud_storage.security.DriveUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserResponseDto login(String username, String password) {
        log.debug("Attempting to authenticate user: {}", username);

        try {
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(username, password);

            Authentication authentication = authenticationManager.authenticate(authToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            DriveUserDetails principal = (DriveUserDetails) authentication.getPrincipal();

            log.info("User successfully authenticated: {}", username);
            return new UserResponseDto(principal.getUsername());

        } catch (BadCredentialsException e) {
            log.warn("Invalid credentials for user: {}", username);
            throw e;

        } catch (AuthenticationException e) {
            log.error("Authentication failed for user: {}", username, e);
            throw e;
        }
    }

    @Transactional
    public void createUser(UserRequestDto userRequest) {
        log.info("Registering new user: {}", userRequest.username());

        if (userRepository.existsByUsernameIgnoreCase(userRequest.username())) {
            log.warn("Registration attempt with existing username: {}", userRequest.username());
            throw new IllegalArgumentException("Username already exist with name: " + userRequest.username());
        }

        User user = userMapper.toEntity(userRequest);

        var raw = user.getPassword();
        user.setPassword(passwordEncoder.encode(raw));

        userRepository.save(user);
        log.info("User registered successfully: {}", user.getUsername());
    }

}