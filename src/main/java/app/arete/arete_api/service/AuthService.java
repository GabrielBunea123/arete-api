package app.arete.arete_api.service;

import app.arete.arete_api.api.model.GoogleLoginRequestDto;
import app.arete.arete_api.api.model.RegisterUserRequestDto;
import app.arete.arete_api.api.model.UserLoginRequestDto;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.exception.UnauthorizedException;
import app.arete.arete_api.model.dto.auth.AuthTokens;
import app.arete.arete_api.model.dto.auth.RegistrationResult;

public interface AuthService {

    /**
     * Creates a password account with the {@code USER} role and signs it in. The email is stored trimmed and
     * lower-cased.
     *
     * @param registerUserRequestDto the new account's name, email and password (entered twice)
     * @return the created user and the tokens for its first session
     * @throws BadRequestException if the password and its confirmation differ
     * @throws ConflictException   if an account with this email already exists
     */
    RegistrationResult register(final RegisterUserRequestDto registerUserRequestDto);

    /**
     * Signs in with email and password and starts a new session.
     *
     * @param userLoginRequestDto the email and password
     * @return the tokens for the new session
     * @throws UnauthorizedException if the email is unknown or the password is wrong; both give the same message,
     *                               so callers can't probe which emails exist
     */
    AuthTokens login(final UserLoginRequestDto userLoginRequestDto);

    /**
     * Signs in with a Google ID token and starts a new session. The user is found by the Google subject; if none is
     * linked yet, the Google identity is linked to the account with the same email, or a new account is created.
     * Linking to an account whose email was never verified removes its password and revokes its sessions, so whoever
     * registered that email before can no longer sign in.
     *
     * @param googleLoginRequestDto the ID token from Google Sign-In
     * @return the tokens for the new session
     * @throws UnauthorizedException if the ID token is invalid, or no account is linked yet and Google hasn't
     *                               verified the email
     * @throws ConflictException     if the account with this email is already linked to a different Google account
     */
    AuthTokens loginWithGoogle(final GoogleLoginRequestDto googleLoginRequestDto);

    /**
     * Exchanges a refresh token for a new access token and a new refresh token, see
     * {@link RefreshTokenService#rotateRefreshToken(String)}.
     *
     * @param refreshToken the raw refresh token from the cookie; may be {@code null}
     * @return the new session tokens
     * @throws UnauthorizedException if the refresh token is missing, unknown, expired or already used
     */
    AuthTokens refresh(final String refreshToken);

    /**
     * Ends the session by revoking every token in the refresh token's family. Does nothing if the token is missing
     * or unknown, so logging out always succeeds.
     *
     * @param refreshToken the raw refresh token from the cookie; may be {@code null}
     */
    void logout(final String refreshToken);
}
