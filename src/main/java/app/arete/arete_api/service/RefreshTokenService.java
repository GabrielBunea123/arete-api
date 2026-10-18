package app.arete.arete_api.service;

import app.arete.arete_api.exception.UnauthorizedException;
import app.arete.arete_api.model.dto.auth.AuthTokens;
import app.arete.arete_api.model.entity.User;

public interface RefreshTokenService {

    /**
     * Starts a new token family for a fresh sign-in. Only the token's hash is stored.
     *
     * @param user the user signing in
     * @return the raw refresh token, to be sent to the client in the refresh cookie
     */
    String issueRefreshToken(final User user);

    /**
     * Swaps a valid refresh token for a new access token and a successor refresh token in the same family, and
     * marks the presented token as rotated. Presenting a token that was already rotated is treated as a race
     * between two tabs if it happens within a short grace period (rejected, family kept), and as token theft after
     * that (rejected, whole family revoked, newest token included).
     *
     * @param rawRefreshToken the raw refresh token from the cookie; may be {@code null}
     * @return the new access token and the successor refresh token
     * @throws UnauthorizedException if the token is missing, unknown, expired, revoked or already rotated
     */
    AuthTokens rotateRefreshToken(final String rawRefreshToken);

    /**
     * Revokes every token in the family of the given token, ending that session on all its rotations. Does nothing
     * if the token is blank or unknown.
     *
     * @param rawRefreshToken the raw refresh token from the cookie; may be {@code null}
     */
    void revokeRefreshTokenFamily(final String rawRefreshToken);

    /**
     * Revokes every refresh token of the user, ending all their sessions once the current access tokens expire.
     *
     * @param user the user whose sessions end
     */
    void revokeAllUserRefreshTokens(final User user);
}
