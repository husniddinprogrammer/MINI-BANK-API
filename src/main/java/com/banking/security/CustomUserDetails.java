package com.banking.security;

import com.banking.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Adapter that wraps a {@link User} entity as a Spring Security {@link UserDetails}.
 *
 * <p>Using a separate adapter (rather than having {@code User} implement {@code UserDetails})
 * decouples the security layer from the JPA entity — changes to entity mapping
 * cannot accidentally break authentication contracts.
 *
 * <p>Carries the user's UUID in addition to the email principal so that JWT claims
 * can be populated without an extra DB round-trip.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * Constructs a {@code CustomUserDetails} from the given {@link User} entity.
     *
     * @param user the persistent user entity; must not be null
     */
    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.enabled = user.isEnabled();
        this.accountNonLocked = user.isAccountNonLocked();
        this.authorities = List.of(new SimpleGrantedAuthority(user.getRole().name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
