package app.arete.arete_api.service;

import app.arete.arete_api.model.dto.auth.AuthTokens;
import app.arete.arete_api.model.entity.User;

public interface RefreshTokenService {

    /**
     * Starts a new token family for a fresh sign-in and returns the raw token for the cookie.
     */
    String issueRefreshToken(final User user);

    /**
     * Swaps a valid refresh token for a new access token and a successor refresh token in the same family.
     */
    AuthTokens rotateRefreshToken(final String rawRefreshToken);

    void revokeRefreshTokenFamily(final String rawRefreshToken);

    void revokeAllUserRefreshTokens(final User user);
}
