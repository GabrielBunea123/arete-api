package app.arete.arete_api.mapper;

import app.arete.arete_api.api.model.CalendarDayResponseDto;
import app.arete.arete_api.api.model.DayOfWeekDto;
import app.arete.arete_api.api.model.DayStatusDto;
import app.arete.arete_api.api.model.ProgramCalendarResponseDto;
import app.arete.arete_api.api.model.ProgramDayPageResponseDto;
import app.arete.arete_api.api.model.ProgramDayResponseDto;
import app.arete.arete_api.api.model.ProgramDaySortFieldDto;
import app.arete.arete_api.api.model.WorkoutPartRequestDto;
import app.arete.arete_api.api.model.WorkoutPartResponseDto;
import app.arete.arete_api.model.dto.program.ProgramDayFilterDto;
import app.arete.arete_api.model.entity.Program;
import app.arete.arete_api.model.entity.ProgramDay;
import app.arete.arete_api.model.entity.WorkoutPart;
import app.arete.arete_api.util.enums.DayStatus;
import app.arete.arete_api.util.enums.ProgramDaySortField;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;
import org.springframework.data.domain.Page;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Mapper(config = CentralMapperConfig.class)
public interface ProgramDayMapper {

    @Mapping(target = "date", source = "dayDate")
    @Mapping(target = "dayOfWeek", source = "dayDate.dayOfWeek")
    ProgramDayResponseDto toProgramDayResponseDto(final ProgramDay programDay);

    WorkoutPartResponseDto toWorkoutPartResponseDto(final WorkoutPart workoutPart);

    @Mapping(target = "page", source = "number")
    ProgramDayPageResponseDto toProgramDayPageResponseDto(final Page<ProgramDay> programDayPage);

    @Mapping(target = "date", source = "date")
    @Mapping(target = "dayOfWeek", source = "date.dayOfWeek")
    @Mapping(target = "day", source = "programDay")
    CalendarDayResponseDto toCalendarDayResponseDto(final LocalDate date, final ProgramDay programDay);

    ProgramCalendarResponseDto toProgramCalendarResponseDto(
            final Long programId,
            final LocalDate from,
            final LocalDate to,
            final List<CalendarDayResponseDto> days);

    /**
     * Parts are set by the service through {@link ProgramDay#replaceParts}, which also assigns their order.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "notes", ignore = true)
    @Mapping(target = "parts", ignore = true)
    ProgramDay toProgramDay(final Program program, final LocalDate dayDate, final DayStatus status);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "programDay", ignore = true)
    @Mapping(target = "orderIndex", ignore = true)
    @Mapping(target = "completed", ignore = true)
    @Mapping(target = "resultNotes", ignore = true)
    WorkoutPart toWorkoutPart(final WorkoutPartRequestDto workoutPartRequestDto);

    List<WorkoutPart> toWorkoutParts(final List<WorkoutPartRequestDto> workoutPartRequestDtos);

    /**
     * A copy of the planned content of a part, without feedback, for adding it to another day.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "programDay", ignore = true)
    @Mapping(target = "orderIndex", ignore = true)
    @Mapping(target = "completed", ignore = true)
    @Mapping(target = "resultNotes", ignore = true)
    WorkoutPart copyWorkoutPart(final WorkoutPart workoutPart);

    List<WorkoutPart> copyWorkoutParts(final Collection<WorkoutPart> workoutParts);

    @BeanMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    ProgramDayFilterDto toProgramDayFilterDto(final LocalDate from, final LocalDate to, final DayStatusDto status);

    ProgramDaySortField toProgramDaySortField(final ProgramDaySortFieldDto programDaySortFieldDto);

    DayStatus toDayStatus(final DayStatusDto dayStatusDto);

    DayOfWeekDto toDayOfWeekDto(final DayOfWeek dayOfWeek);
}
