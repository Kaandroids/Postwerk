package com.postwerk.service;

import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Centralized service for resolving the authenticated user's UUID from Spring Security's {@link UserDetails}.
 *
 * <p>All controllers that require the current user's ID should inject this service instead of
 * directly depending on {@link UserRepository}. This enforces the Dependency Inversion Principle
 * and keeps the controller layer free of repository-level concerns.</p>
 *
 * @since 1.0
 */
@Service
public class UserIdResolverService {

    private final UserRepository userRepository;

    public UserIdResolverService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Resolves the UUID of the currently authenticated user.
     *
     * @param userDetails the Spring Security principal containing the user's email
     * @return the user's UUID
     * @throws ResourceNotFoundException if no user exists with the given email
     */
    public UUID resolve(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", userDetails.getUsername()))
                .getId();
    }
}
