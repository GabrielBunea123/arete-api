package app.arete.arete_api.service.implementations;

import app.arete.arete_api.repository.UserRepository;
import app.arete.arete_api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads password accounts for {@code DaoAuthenticationProvider}. Accounts without a password (Google-only) count as
 * not found, so they can't sign in with a password; the provider reports both cases as the same bad-credentials failure.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final String NO_PASSWORD_ACCOUNT_DETAIL = "No password account for this email";

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(final @NonNull String email) {
        return userRepository.findByEmail(email)
                .filter(user -> user.getPasswordHash() != null)
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException(NO_PASSWORD_ACCOUNT_DETAIL));
    }
}
