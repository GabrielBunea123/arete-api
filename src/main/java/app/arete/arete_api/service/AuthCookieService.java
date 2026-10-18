package app.arete.arete_api.service;

import app.arete.arete_api.model.dto.auth.AuthTokens;
import org.springframework.http.HttpHeaders;

public interface AuthCookieService {

    /**
     * Builds the {@code Set-Cookie} headers for a signed-in session: the HttpOnly access token cookie (sent on every
     * request) and the HttpOnly refresh token cookie (only sent to the auth endpoints).
     *
     * @param authTokens the access token and raw refresh token of the session
     * @return headers holding both {@code Set-Cookie} values
     */
    HttpHeaders buildSessionCookies(final AuthTokens authTokens);

    /**
     * Builds {@code Set-Cookie} headers that expire both session cookies immediately.
     *
     * @return headers holding both expiring {@code Set-Cookie} values
     */
    HttpHeaders buildClearedCookies();
}
