package app.arete.arete_api.config.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "arete.program")
public record ProgramProperties(
        @Positive int calendarMaxDays
) {
}
