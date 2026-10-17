package app.arete.arete_api.service;

import app.arete.arete_api.api.model.GoogleLoginRequestDto;
import app.arete.arete_api.api.model.RegisterUserRequestDto;
import app.arete.arete_api.api.model.UserLoginRequestDto;
import app.arete.arete_api.model.dto.auth.AuthTokens;
import app.arete.arete_api.model.dto.auth.RegistrationResult;

public interface AuthService {

    RegistrationResult register(final RegisterUserRequestDto registerUserRequestDto);

    AuthTokens login(final UserLoginRequestDto userLoginRequestDto);

    AuthTokens loginWithGoogle(final GoogleLoginRequestDto googleLoginRequestDto);

    AuthTokens refresh(final String refreshToken);

    void logout(final String refreshToken);
}
