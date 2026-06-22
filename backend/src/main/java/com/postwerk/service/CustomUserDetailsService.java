package com.postwerk.service;

import com.postwerk.model.enums.StaffPermission;
import com.postwerk.repository.UserRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security {@link UserDetailsService} implementation that loads user credentials
 * from the application's {@link com.postwerk.repository.UserRepository} by email address.
 *
 * <p>Rejects soft-deleted or admin-disabled users by checking {@code deletedAt}.</p>
 *
 * @since 1.0
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        // Reject soft-deleted / admin-disabled users
        if (user.getDeletedAt() != null) {
            throw new DisabledException("User account is disabled");
        }

        // Coarse role first (so the JWT 'role' claim, derived from the first authority, stays
        // correct), then — for platform staff — a ROLE_STAFF marker plus one authority per
        // fine-grained StaffPermission so admin endpoints can authorize on a discrete capability.
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        if (user.getStaffRole() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_STAFF"));
            for (StaffPermission permission : user.getStaffRole().permissions()) {
                authorities.add(new SimpleGrantedAuthority(permission.name()));
            }
        }
        return new User(user.getEmail(), user.getPasswordHash(), authorities);
    }
}
