package app.arete.arete_api.security;

import app.arete.arete_api.config.properties.GoogleProperties;
import app.arete.arete_api.exception.UnauthorizedException;
import app.arete.arete_api.model.dto.auth.GoogleIdentity;
import app.arete.arete_api.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Collection;

/**
 * Verifies Google ID tokens: RS256 signature against Google's published keys, expiry, issuer, and that the
 * audience is this app's OAuth client id (so a token minted for another app is rejected).
 */
@Slf4j
@Component
public class GoogleIdTokenVerifier {

    private static final String EMAIL_LOCAL_PART_SEPARATOR = "@";

    private final JwtDecoder jwtDecoder;

    public GoogleIdTokenVerifier(final GoogleProperties googleProperties, final Clock clock) {
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(googleProperties.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        final JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtClaimValidator<>(JwtClaimNames.ISS,
                        (final Object issuer) -> issuer != null && googleProperties.issuers().contains(issuer.toString())),
                new JwtClaimValidator<>(JwtClaimNames.AUD,
                        (final Collection<?> audience) -> audience != null && audience.contains(googleProperties.clientId()))));
        this.jwtDecoder = decoder;
    }

    public GoogleIdentity verify(final String idToken) {
        final Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (final JwtException exception) {
            log.debug("Rejected Google ID token: {}", exception.getMessage());
            throw new UnauthorizedException("Invalid Google ID token");
        }

        final String email = jwt.getClaimAsString(StandardClaimNames.EMAIL);
        if (email == null || email.isBlank()) {
            throw new UnauthorizedException("Google ID token has no email");
        }
        return new GoogleIdentity(
                jwt.getSubject(),
                email,
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(StandardClaimNames.EMAIL_VERIFIED)),
                resolveFullName(jwt.getClaimAsString(StandardClaimNames.NAME), email));
    }

    private static String resolveFullName(final String name, final String email) {
        final String fullName = name == null || name.isBlank()
                ? email.substring(0, email.indexOf(EMAIL_LOCAL_PART_SEPARATOR))
                : name.strip();
        return fullName.length() > Constants.FULL_NAME_MAX_LENGTH
                ? fullName.substring(0, Constants.FULL_NAME_MAX_LENGTH)
                : fullName;
    }
}
