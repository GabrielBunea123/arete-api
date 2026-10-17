package app.arete.arete_api.security;

import app.arete.arete_api.model.entity.User;
import app.arete.arete_api.util.Constants;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Spring Security's view of a password account during login. Wraps the entity so the caller gets the
 * {@link User} back from the {@code Authentication} without loading it again.
 */
public record UserPrincipal(User user) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(Constants.ROLE_PREFIX + role.getValue()))
                .toList();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }
}
