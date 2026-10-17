package app.arete.arete_api.controller;

import app.arete.arete_api.api.AuthApi;
import app.arete.arete_api.api.model.GoogleLoginRequestDto;
import app.arete.arete_api.api.model.RegisterUserRequestDto;
import app.arete.arete_api.api.model.UserLoginRequestDto;
import app.arete.arete_api.api.model.UserResponseDto;
import app.arete.arete_api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final UserService userService;

    @Override
    public ResponseEntity<UserResponseDto> registerUser(final RegisterUserRequestDto registerUserRequestDto) {
        final UserResponseDto userResponseDto = userService.register(registerUserRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(userResponseDto);
    }

    // TODO: implement once AuthService (JWT cookies, Google ID token verification, refresh rotation) exists
    @Override
    public ResponseEntity<Void> loginUser(final UserLoginRequestDto userLoginRequestDto) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<Void> loginWithGoogle(final GoogleLoginRequestDto googleLoginRequestDto) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<Void> refreshSession() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<Void> logoutUser() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<Void> getCsrfToken() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
