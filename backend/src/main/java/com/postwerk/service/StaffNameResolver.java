package com.postwerk.service;

import com.postwerk.model.User;
import com.postwerk.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the display name of an acting staff member for change-history / event labels. Extracted
 * from the admin services that each carried an identical private {@code staffName} helper.
 *
 * @since 1.0
 */
@Component
public class StaffNameResolver {

    private final UserRepository userRepository;

    public StaffNameResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Display name of the actor: {@code "system"} when {@code null}, {@code "staff"} when not found. */
    public String of(UUID actorUserId) {
        if (actorUserId == null) return "system";
        return userRepository.findById(actorUserId).map(User::getFullName).orElse("staff");
    }
}
