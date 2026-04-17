package com.banking.security;

import com.banking.entity.User;
import com.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads user data from the database for Spring Security's authentication machinery.
 *
 * <p>Spring Security calls this during {@code AuthenticationManager.authenticate()}.
 * The returned {@link UserDetails} is used to verify credentials and populate the
 * security context.
 *
 * <p>Security note: the error message deliberately does not reveal whether the email
 * exists. The service layer adds a deliberate constant-time check to prevent timing
 * attacks when this generic message alone is not sufficient.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user by email address (used as the principal identifier).
     *
     * @param email the email submitted in the login request
     * @return {@link CustomUserDetails} wrapping the persisted {@link User}
     * @throws UsernameNotFoundException if no user with the given email exists
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading user by email: {}", email);

        User user = userRepository.findByEmail(email)
            // Generic message — does not reveal whether the email is registered
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        return new CustomUserDetails(user);
    }
}
