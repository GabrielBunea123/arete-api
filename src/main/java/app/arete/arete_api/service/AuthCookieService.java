package app.arete.arete_api.service;

import app.arete.arete_api.model.dto.auth.AuthTokens;
import org.springframework.http.HttpHeaders;

public interface AuthCookieService {

    /**
     * Builds the {@code Set-Cookie} headers for a signed-in session: the HttpOnly access token cookie (sent on every
     * request) and the HttpOnly refresh token cookie (only sent to the auth endpoints).
     */
    HttpHeaders buildSessionCookies(final AuthTokens authTokens);

    /**
     * Builds {@code Set-Cookie} headers that expire both session cookies immediately.
     */
    HttpHeaders buildClearedCookies();
}
