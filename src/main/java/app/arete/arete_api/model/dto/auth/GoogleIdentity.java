package app.arete.arete_api.model.dto.auth;

public record GoogleIdentity(
        String subject,
        String email,
        boolean emailVerified,
        String fullName
) {
}
