package app.arete.arete_api.security;

import app.arete.arete_api.config.properties.JwtProperties;
import app.arete.arete_api.exception.UnauthorizedException;
import app.arete.arete_api.model.entity.User;
import app.arete.arete_api.util.Constants;
import app.arete.arete_api.util.enums.RoleEnum;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Issues and verifies the API's own HS256 access tokens. The subject is the user id.
 */

@Component
public class JwtService {

    private static final MacAlgorithm SIGNING_ALGORITHM = MacAlgorithm.HS256;
    private static final String HMAC_KEY_ALGORITHM = "HmacSHA256";

    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtService(final JwtProperties jwtProperties, final Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        final SecretKey secretKey = buildSecretKey(jwtProperties.secret());
        this.jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey).algorithm(SIGNING_ALGORITHM).build();
        this.jwtDecoder = buildDecoder(secretKey, jwtProperties.issuer(), clock);
    }

    public String generateAccessToken(final User user) {
        final Instant now = clock.instant();
        final List<String> roles = user.getRoles().stream().map(RoleEnum::getValue).toList();
        final JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessExpiration()))
                .claim(Constants.ROLES_CLAIM, roles)
                .build();
        final JwsHeader header = JwsHeader.with(SIGNING_ALGORITHM).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Jwt parseAccessToken(final String token) {
        try {
            return jwtDecoder.decode(token);
        } catch (final JwtException exception) {
            throw new UnauthorizedException("Invalid or expired access token");
        }
    }

    private static SecretKey buildSecretKey(final String base64Secret) {
        final byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
        if (keyBytes.length < Constants.MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(String.format(
                    "arete.jwt.secret must decode to at least %d bytes", Constants.MIN_JWT_SECRET_BYTES));
        }
        return new SecretKeySpec(keyBytes, HMAC_KEY_ALGORITHM);
    }

    private static JwtDecoder buildDecoder(final SecretKey secretKey, final String issuer, final Clock clock) {
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(SIGNING_ALGORITHM)
                .build();
        final JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(issuer)));
        return decoder;
    }
}
