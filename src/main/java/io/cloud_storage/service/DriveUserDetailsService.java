package io.cloud_storage.service;

import io.cloud_storage.domain.model.User;
import io.cloud_storage.repository.UserRepository;
import io.cloud_storage.security.DriveUserDetails;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DriveUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + username));

        return new DriveUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPassword()
        );
    }
}
