package app.arete.arete_api.config.properties;

import app.arete.arete_api.util.enums.SameSite;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CookieProperties(
        boolean secure,
        @NotNull SameSite accessSameSite,
        @NotNull SameSite refreshSameSite,
        @NotBlank String refreshPath
) {
}
