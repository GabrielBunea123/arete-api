package app.arete.arete_api.controller;

import app.arete.arete_api.api.WorkoutPartApi;
import app.arete.arete_api.api.model.ProgramDayResponseDto;
import app.arete.arete_api.api.model.ReorderWorkoutPartsRequestDto;
import app.arete.arete_api.api.model.WorkoutPartRequestDto;
import app.arete.arete_api.api.model.WorkoutPartResponseDto;
import app.arete.arete_api.service.WorkoutPartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class WorkoutPartController implements WorkoutPartApi {

    private final WorkoutPartService workoutPartService;

    @Override
    public ResponseEntity<WorkoutPartResponseDto> createWorkoutPart(
            final Long programId,
            final LocalDate date,
            final WorkoutPartRequestDto workoutPartRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workoutPartService.createUserWorkoutPart(programId, date, workoutPartRequestDto));
    }

    @Override
    public ResponseEntity<WorkoutPartResponseDto> updateWorkoutPart(
            final Long programId,
            final LocalDate date,
            final Long partId,
            final WorkoutPartRequestDto workoutPartRequestDto) {
        return ResponseEntity.ok(
                workoutPartService.updateUserWorkoutPart(programId, date, partId, workoutPartRequestDto));
    }

    @Override
    public ResponseEntity<Void> deleteWorkoutPart(final Long programId, final LocalDate date, final Long partId) {
        workoutPartService.deleteUserWorkoutPart(programId, date, partId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ProgramDayResponseDto> reorderWorkoutParts(
            final Long programId,
            final LocalDate date,
            final ReorderWorkoutPartsRequestDto reorderWorkoutPartsRequestDto) {
        return ResponseEntity.ok(
                workoutPartService.reorderUserWorkoutParts(programId, date, reorderWorkoutPartsRequestDto));
    }
}
