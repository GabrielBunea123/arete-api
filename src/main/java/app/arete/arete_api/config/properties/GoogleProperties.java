package app.arete.arete_api.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "arete.google")
public record GoogleProperties(
        @NotBlank String clientId,
        @NotBlank String jwkSetUri,
        @NotEmpty Set<String> issuers
) {
}
