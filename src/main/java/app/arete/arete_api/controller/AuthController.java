package app.arete.arete_api.controller;

import app.arete.arete_api.api.AuthApi;
import app.arete.arete_api.api.model.GoogleLoginRequestDto;
import app.arete.arete_api.api.model.RegisterUserRequestDto;
import app.arete.arete_api.api.model.UserLoginRequestDto;
import app.arete.arete_api.api.model.UserResponseDto;
import app.arete.arete_api.model.dto.auth.AuthTokens;
import app.arete.arete_api.model.dto.auth.RegistrationResult;
import app.arete.arete_api.service.AuthCookieService;
import app.arete.arete_api.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    @Override
    public ResponseEntity<UserResponseDto> registerUser(final RegisterUserRequestDto registerUserRequestDto) {
        final RegistrationResult registrationResult = authService.register(registerUserRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .headers(authCookieService.buildSessionCookies(registrationResult.tokens()))
                .body(registrationResult.user());
    }

    @Override
    public ResponseEntity<Void> loginUser(final UserLoginRequestDto userLoginRequestDto) {
        return buildSessionResponse(authService.login(userLoginRequestDto));
    }

    @Override
    public ResponseEntity<Void> loginWithGoogle(final GoogleLoginRequestDto googleLoginRequestDto) {
        return buildSessionResponse(authService.loginWithGoogle(googleLoginRequestDto));
    }

    @Override
    public ResponseEntity<Void> refreshSession(final String refreshToken) {
        return buildSessionResponse(authService.refresh(refreshToken));
    }

    @Override
    public ResponseEntity<Void> logoutUser(final String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().headers(authCookieService.buildClearedCookies()).build();
    }

    @Override
    public ResponseEntity<Void> getCsrfToken() {
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> buildSessionResponse(final AuthTokens authTokens) {
        return ResponseEntity.noContent().headers(authCookieService.buildSessionCookies(authTokens)).build();
    }
}
