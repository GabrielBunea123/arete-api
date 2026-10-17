package app.arete.arete_api.service.implementations;

import app.arete.arete_api.config.properties.JwtProperties;
import app.arete.arete_api.exception.UnauthorizedException;
import app.arete.arete_api.model.dto.auth.AuthTokens;
import app.arete.arete_api.model.entity.RefreshToken;
import app.arete.arete_api.model.entity.User;
import app.arete.arete_api.repository.RefreshTokenRepository;
import app.arete.arete_api.security.JwtService;
import app.arete.arete_api.service.RefreshTokenService;
import app.arete.arete_api.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Two tabs refreshing at the same moment both present the same token. The loser of the row lock sees it
 * rotated a moment ago; that is a race, not theft, so it gets a 401 without revoking the family.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Duration ROTATION_GRACE_PERIOD = Duration.ofSeconds(10);
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String INVALID_REFRESH_TOKEN_DETAIL = "Invalid or expired refresh token";

    private final SecureRandom secureRandom = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    @Override
    @Transactional
    public String issueRefreshToken(final User user) {
        return createRefreshToken(user, UUID.randomUUID(), LocalDateTime.now(clock)).rawToken();
    }

    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthTokens rotateRefreshToken(final String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN_DETAIL);
        }
        final LocalDateTime now = LocalDateTime.now(clock);
        final RefreshToken currentToken = refreshTokenRepository.findByTokenHashForUpdate(hashToken(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN_DETAIL));

        if (currentToken.getRevokedAt() != null) {
            handleRevokedTokenUse(currentToken, now);
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN_DETAIL);
        }
        if (!currentToken.isActive(now)) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN_DETAIL);
        }

        final User user = currentToken.getUser();
        final IssuedRefreshToken successor = createRefreshToken(user, currentToken.getFamilyId(), now);
        currentToken.rotateTo(successor.entity(), now);
        return new AuthTokens(jwtService.generateAccessToken(user), successor.rawToken());
    }

    @Override
    @Transactional
    public void revokeRefreshTokenFamily(final String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), LocalDateTime.now(clock)));
    }

    @Override
    @Transactional
    public void revokeAllUserRefreshTokens(final User user) {
        refreshTokenRepository.revokeAllForUser(user.getId(), LocalDateTime.now(clock));
    }

    private void handleRevokedTokenUse(final RefreshToken revokedToken, final LocalDateTime now) {
        final boolean isConcurrentRotation = revokedToken.getReplacedBy() != null
                && revokedToken.getRevokedAt().plus(ROTATION_GRACE_PERIOD).isAfter(now);
        if (isConcurrentRotation) {
            return;
        }
        log.warn("Refresh token reuse detected for user {}; revoking token family {}",
                revokedToken.getUser().getId(), revokedToken.getFamilyId());
        refreshTokenRepository.revokeFamily(revokedToken.getFamilyId(), now);
    }

    private IssuedRefreshToken createRefreshToken(final User user, final UUID familyId, final LocalDateTime now) {
        final String rawToken = generateRawToken();
        final RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(rawToken))
                .familyId(familyId)
                .expiresAt(now.plus(jwtProperties.refreshExpiration()))
                .build();
        return new IssuedRefreshToken(refreshTokenRepository.save(refreshToken), rawToken);
    }

    private String generateRawToken() {
        final byte[] tokenBytes = new byte[Constants.REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private static String hashToken(final String rawToken) {
        try {
            final MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record IssuedRefreshToken(RefreshToken entity, String rawToken) {
    }
}
