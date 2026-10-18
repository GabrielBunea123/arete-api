package app.arete.arete_api.controller;

import app.arete.arete_api.api.ProgramApi;
import app.arete.arete_api.api.model.ProgramPageResponseDto;
import app.arete.arete_api.api.model.ProgramRequestDto;
import app.arete.arete_api.api.model.ProgramResponseDto;
import app.arete.arete_api.api.model.ProgramSortFieldDto;
import app.arete.arete_api.api.model.SortDirectionDto;
import app.arete.arete_api.mapper.ProgramMapper;
import app.arete.arete_api.model.dto.program.ProgramFilterDto;
import app.arete.arete_api.service.ProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class ProgramController implements ProgramApi {

    private final ProgramService programService;
    private final ProgramMapper programMapper;

    @Override
    public ResponseEntity<ProgramPageResponseDto> getPrograms(
            final Integer page,
            final Integer size,
            final ProgramSortFieldDto sort,
            final SortDirectionDto direction,
            final String name,
            final LocalDate createdFrom,
            final LocalDate createdTo) {
        final ProgramFilterDto programFilterDto = programMapper.toProgramFilterDto(name, createdFrom, createdTo);
        return ResponseEntity.ok(programService.getUserPrograms(page, size, sort, direction, programFilterDto));
    }

    @Override
    public ResponseEntity<ProgramResponseDto> createProgram(final ProgramRequestDto programRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(programService.createUserProgram(programRequestDto));
    }

    @Override
    public ResponseEntity<ProgramResponseDto> getProgram(final Long programId) {
        return ResponseEntity.ok(programService.getUserProgramById(programId));
    }

    @Override
    public ResponseEntity<ProgramResponseDto> updateProgram(
            final Long programId,
            final ProgramRequestDto programRequestDto) {
        return ResponseEntity.ok(programService.updateUserProgram(programId, programRequestDto));
    }

    @Override
    public ResponseEntity<Void> deleteProgram(final Long programId) {
        programService.deleteUserProgram(programId);
        return ResponseEntity.noContent().build();
    }
}
