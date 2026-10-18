package app.arete.arete_api.model.dto.program;

import app.arete.arete_api.util.enums.DayStatus;

import java.time.LocalDate;

public record ProgramDayFilterDto(
        LocalDate from,
        LocalDate to,
        DayStatus status
) {
}
