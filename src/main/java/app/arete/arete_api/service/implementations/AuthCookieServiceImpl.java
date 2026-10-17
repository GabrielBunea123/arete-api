package app.arete.arete_api.service.implementations;

import app.arete.arete_api.config.properties.CookieProperties;
import app.arete.arete_api.config.properties.JwtProperties;
import app.arete.arete_api.model.dto.auth.AuthTokens;
import app.arete.arete_api.service.AuthCookieService;
import app.arete.arete_api.util.Constants;
import app.arete.arete_api.util.enums.SameSite;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Builds the {@code Set-Cookie} headers that carry the session. Both cookies are HttpOnly, so scripts never see the tokens.
 */
@Service
@RequiredArgsConstructor
public class AuthCookieServiceImpl implements AuthCookieService {

    private static final String CLEARED_VALUE = "";

    private final JwtProperties jwtProperties;

    @Override
    public HttpHeaders buildSessionCookies(final AuthTokens authTokens) {
        final CookieProperties cookieProperties = jwtProperties.cookie();
        return buildHeaders(
                buildCookie(Constants.ACCESS_TOKEN_COOKIE, authTokens.accessToken(), Constants.ACCESS_TOKEN_COOKIE_PATH,
                        cookieProperties.accessSameSite(), jwtProperties.accessExpiration()),
                buildCookie(Constants.REFRESH_TOKEN_COOKIE, authTokens.refreshToken(), cookieProperties.refreshPath(),
                        cookieProperties.refreshSameSite(), jwtProperties.refreshExpiration()));
    }

    @Override
    public HttpHeaders buildClearedCookies() {
        final CookieProperties cookieProperties = jwtProperties.cookie();
        return buildHeaders(
                buildCookie(Constants.ACCESS_TOKEN_COOKIE, CLEARED_VALUE, Constants.ACCESS_TOKEN_COOKIE_PATH,
                        cookieProperties.accessSameSite(), Duration.ZERO),
                buildCookie(Constants.REFRESH_TOKEN_COOKIE, CLEARED_VALUE, cookieProperties.refreshPath(),
                        cookieProperties.refreshSameSite(), Duration.ZERO));
    }

    private ResponseCookie buildCookie(
            final String name,
            final String value,
            final String path,
            final SameSite sameSite,
            final Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(jwtProperties.cookie().secure())
                .sameSite(sameSite.getValue())
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private static HttpHeaders buildHeaders(final ResponseCookie accessCookie, final ResponseCookie refreshCookie) {
        final HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, accessCookie.toString());
        headers.add(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        return headers;
    }
}
