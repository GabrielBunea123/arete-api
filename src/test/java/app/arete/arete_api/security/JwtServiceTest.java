package app.arete.arete_api.security;

import app.arete.arete_api.config.properties.CookieProperties;
import app.arete.arete_api.config.properties.JwtProperties;
import app.arete.arete_api.exception.UnauthorizedException;
import app.arete.arete_api.model.entity.User;
import app.arete.arete_api.util.Constants;
import app.arete.arete_api.util.enums.RoleEnum;
import app.arete.arete_api.util.enums.SameSite;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtZm9yLWFyZXRlLWFwaS10ZXN0cy1vbmx5LW5vdC1mb3ItcHJvZA==";
    private static final String OTHER_SECRET = "b3RoZXItc2VjcmV0LWZvci1hcmV0ZS1hcGktdGVzdHMtb25seS1ub3QtZm9yLXByb2Q=";
    private static final String TOO_SHORT_SECRET = "dG9vLXNob3J0";
    private static final String ISSUER = "arete-api";
    private static final Duration ACCESS_EXPIRATION = Duration.ofMinutes(15);
    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");
    private static final long USER_ID = 42L;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(buildProperties(SECRET, ISSUER), clock);

    @Test
    void generatedTokenParsesWithUserIdSubjectAndRoles() {
        final Jwt jwt = jwtService.parseAccessToken(jwtService.generateAccessToken(buildUser()));

        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(USER_ID));
        assertThat(jwt.getClaimAsStringList(Constants.ROLES_CLAIM)).containsExactly(RoleEnum.USER.getValue());
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(ACCESS_EXPIRATION));
    }

    @Test
    void expiredTokenIsRejected() {
        final String token = jwtService.generateAccessToken(buildUser());
        final Clock laterClock = Clock.fixed(NOW.plus(ACCESS_EXPIRATION).plus(Duration.ofMinutes(5)), ZoneOffset.UTC);
        final JwtService laterJwtService = new JwtService(buildProperties(SECRET, ISSUER), laterClock);

        assertThatThrownBy(() -> laterJwtService.parseAccessToken(token)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        final JwtService otherKeyJwtService = new JwtService(buildProperties(OTHER_SECRET, ISSUER), clock);
        final String forgedToken = otherKeyJwtService.generateAccessToken(buildUser());

        assertThatThrownBy(() -> jwtService.parseAccessToken(forgedToken)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        final JwtService otherIssuerJwtService = new JwtService(buildProperties(SECRET, "someone-else"), clock);
        final String token = otherIssuerJwtService.generateAccessToken(buildUser());

        assertThatThrownBy(() -> jwtService.parseAccessToken(token)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void garbageTokenIsRejected() {
        assertThatThrownBy(() -> jwtService.parseAccessToken("not-a-jwt")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void secretShorterThan32BytesFailsAtStartup() {
        assertThatThrownBy(() -> new JwtService(buildProperties(TOO_SHORT_SECRET, ISSUER), clock))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JwtProperties buildProperties(final String secret, final String issuer) {
        return new JwtProperties(
                secret,
                issuer,
                ACCESS_EXPIRATION,
                Duration.ofDays(30),
                new CookieProperties(true, SameSite.LAX, SameSite.STRICT, "/api/v1/auth"));
    }

    private static User buildUser() {
        return User.builder()
                .id(USER_ID)
                .email("jane@example.com")
                .fullName("Jane Doe")
                .roles(EnumSet.of(RoleEnum.USER))
                .build();
    }
}
