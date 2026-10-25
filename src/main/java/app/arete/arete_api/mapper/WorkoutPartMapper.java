package app.arete.arete_api.mapper;

import app.arete.arete_api.api.model.WorkoutPartRequestDto;
import app.arete.arete_api.api.model.WorkoutPartResponseDto;
import app.arete.arete_api.model.entity.WorkoutPart;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface WorkoutPartMapper {

    WorkoutPartResponseDto toWorkoutPartResponseDto(final WorkoutPart workoutPart);

    /**
     * The order is assigned by {@code ProgramDay#addPart}, and a new part starts without feedback.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "programDay", ignore = true)
    @Mapping(target = "orderIndex", ignore = true)
    @Mapping(target = "completed", ignore = true)
    @Mapping(target = "resultNotes", ignore = true)
    WorkoutPart toWorkoutPart(final WorkoutPartRequestDto workoutPartRequestDto);
}
