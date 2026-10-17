package app.arete.arete_api.model.dto.auth;

import app.arete.arete_api.api.model.UserResponseDto;

public record RegistrationResult(
        UserResponseDto user,
        AuthTokens tokens
) {
}
