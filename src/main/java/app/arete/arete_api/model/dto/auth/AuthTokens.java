package app.arete.arete_api.model.dto.auth;

public record AuthTokens(
        String accessToken,
        String refreshToken
) {
}
