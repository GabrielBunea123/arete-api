package app.arete.arete_api.model.dto.program;

import java.time.LocalDate;

public record ProgramFilterDto(
        String name,
        LocalDate createdFrom,
        LocalDate createdTo
) {
}
