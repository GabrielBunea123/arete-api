package app.arete.arete_api.controller;

import app.arete.arete_api.api.ProgramDayApi;
import app.arete.arete_api.api.model.DayStatusDto;
import app.arete.arete_api.api.model.MoveProgramDayRequestDto;
import app.arete.arete_api.api.model.ProgramCalendarResponseDto;
import app.arete.arete_api.api.model.ProgramDayFeedbackRequestDto;
import app.arete.arete_api.api.model.ProgramDayPageResponseDto;
import app.arete.arete_api.api.model.ProgramDayResponseDto;
import app.arete.arete_api.api.model.ProgramDaySortFieldDto;
import app.arete.arete_api.api.model.SortDirectionDto;
import app.arete.arete_api.mapper.ProgramDayMapper;
import app.arete.arete_api.model.dto.program.ProgramDayFilterDto;
import app.arete.arete_api.service.ProgramDayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class ProgramDayController implements ProgramDayApi {

    private final ProgramDayService programDayService;
    private final ProgramDayMapper programDayMapper;

    @Override
    public ResponseEntity<ProgramCalendarResponseDto> getProgramCalendar(
            final Long programId,
            final LocalDate from,
            final LocalDate to) {
        return ResponseEntity.ok(programDayService.getUserProgramCalendar(programId, from, to));
    }

    @Override
    public ResponseEntity<ProgramDayPageResponseDto> getProgramDays(
            final Long programId,
            final Integer page,
            final Integer size,
            final ProgramDaySortFieldDto sort,
            final SortDirectionDto direction,
            final LocalDate from,
            final LocalDate to,
            final DayStatusDto status) {
        final ProgramDayFilterDto programDayFilterDto = programDayMapper.toProgramDayFilterDto(from, to, status);
        return ResponseEntity.ok(
                programDayService.getUserProgramDays(programId, page, size, sort, direction, programDayFilterDto));
    }

    @Override
    public ResponseEntity<ProgramDayResponseDto> getProgramDay(final Long programId, final LocalDate date) {
        return ResponseEntity.ok(programDayService.getUserProgramDay(programId, date));
    }

    @Override
    public ResponseEntity<Void> deleteProgramDay(final Long programId, final LocalDate date) {
        programDayService.deleteUserProgramDay(programId, date);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ProgramDayResponseDto> updateProgramDayFeedback(
            final Long programId,
            final LocalDate date,
            final ProgramDayFeedbackRequestDto programDayFeedbackRequestDto) {
        return ResponseEntity.ok(
                programDayService.updateUserProgramDayFeedback(programId, date, programDayFeedbackRequestDto));
    }

    @Override
    public ResponseEntity<ProgramDayResponseDto> moveProgramDay(
            final Long programId,
            final LocalDate date,
            final MoveProgramDayRequestDto moveProgramDayRequestDto) {
        return ResponseEntity.ok(programDayService.moveUserProgramDay(programId, date, moveProgramDayRequestDto));
    }
}
