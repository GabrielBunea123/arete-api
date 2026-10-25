package app.arete.arete_api.service.implementations;

import app.arete.arete_api.api.model.ProgramDayResponseDto;
import app.arete.arete_api.api.model.ReorderWorkoutPartsRequestDto;
import app.arete.arete_api.api.model.WorkoutPartRequestDto;
import app.arete.arete_api.api.model.WorkoutPartResponseDto;
import app.arete.arete_api.config.properties.ProgramProperties;
import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.mapper.ProgramDayMapper;
import app.arete.arete_api.mapper.WorkoutPartMapper;
import app.arete.arete_api.model.entity.ProgramDay;
import app.arete.arete_api.model.entity.WorkoutPart;
import app.arete.arete_api.repository.ProgramDayRepository;
import app.arete.arete_api.service.ProgramDayService;
import app.arete.arete_api.service.WorkoutPartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class WorkoutPartServiceImpl implements WorkoutPartService {

    private final ProgramDayRepository programDayRepository;
    private final ProgramDayService programDayService;
    private final WorkoutPartMapper workoutPartMapper;
    private final ProgramDayMapper programDayMapper;
    private final ProgramProperties programProperties;

    @Override
    @Transactional
    public WorkoutPartResponseDto createUserWorkoutPart(
            final Long programId,
            final LocalDate date,
            final WorkoutPartRequestDto workoutPartRequestDto) {
        final ProgramDay programDay = programDayService.getOrCreateUserProgramDayEntity(programId, date);
        if (programDay.getParts().size() >= programProperties.maxPartsPerDay()) {
            throw new ConflictException(
                    "A day can have at most %d workout parts".formatted(programProperties.maxPartsPerDay()));
        }
        programDay.addPart(workoutPartMapper.toWorkoutPart(workoutPartRequestDto));
        final ProgramDay savedProgramDay = programDayRepository.saveAndFlush(programDay);
        return workoutPartMapper.toWorkoutPartResponseDto(getLastPart(savedProgramDay));
    }

    @Override
    @Transactional
    public WorkoutPartResponseDto updateUserWorkoutPart(
            final Long programId,
            final LocalDate date,
            final Long partId,
            final WorkoutPartRequestDto workoutPartRequestDto) {
        final ProgramDay programDay = programDayService.getUserProgramDayEntity(programId, date);
        final WorkoutPart workoutPart = programDay.updatePart(
                partId,
                workoutPartRequestDto.getTitle(),
                workoutPartRequestDto.getDescription(),
                workoutPartRequestDto.getDurationMinutes());
        programDayRepository.saveAndFlush(programDay);
        return workoutPartMapper.toWorkoutPartResponseDto(workoutPart);
    }

    @Override
    @Transactional
    public void deleteUserWorkoutPart(final Long programId, final LocalDate date, final Long partId) {
        final ProgramDay programDay = programDayService.getUserProgramDayEntity(programId, date);
        programDay.removePart(partId);
        if (programDay.getParts().isEmpty()) {
            programDayRepository.delete(programDay);
            return;
        }
        programDayRepository.save(programDay);
    }

    @Override
    @Transactional
    public ProgramDayResponseDto reorderUserWorkoutParts(
            final Long programId,
            final LocalDate date,
            final ReorderWorkoutPartsRequestDto reorderWorkoutPartsRequestDto) {
        final ProgramDay programDay = programDayService.getUserProgramDayEntity(programId, date);
        programDay.reorderParts(reorderWorkoutPartsRequestDto.getPartIds());
        return programDayMapper.toProgramDayResponseDto(programDayRepository.saveAndFlush(programDay));
    }

    private static WorkoutPart getLastPart(final ProgramDay programDay) {
        return programDay.getParts().stream()
                .max(Comparator.comparingInt(WorkoutPart::getOrderIndex))
                .orElseThrow();
    }
}
