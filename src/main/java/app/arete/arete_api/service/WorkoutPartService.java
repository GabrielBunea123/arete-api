package app.arete.arete_api.service;

import app.arete.arete_api.api.model.ProgramDayResponseDto;
import app.arete.arete_api.api.model.ReorderWorkoutPartsRequestDto;
import app.arete.arete_api.api.model.WorkoutPartRequestDto;
import app.arete.arete_api.api.model.WorkoutPartResponseDto;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.exception.NotFoundException;

import java.time.LocalDate;

/**
 * The workout parts of the days in the signed-in user's programs. Every method works only on programs the caller owns
 * and throws {@link NotFoundException} for a missing program or another user's program.
 */
public interface WorkoutPartService {

    /**
     * Adds a part after the existing parts of a date. A rest day becomes a {@code PLANNED} day with this part.
     *
     * @param programId             the program id
     * @param date                  the date of the day
     * @param workoutPartRequestDto the content of the part
     * @return the created part
     * @throws NotFoundException if the program doesn't exist or belongs to another user
     * @throws ConflictException if the day already has {@code arete.program.max-parts-per-day} parts
     */
    WorkoutPartResponseDto createUserWorkoutPart(
            final Long programId,
            final LocalDate date,
            final WorkoutPartRequestDto workoutPartRequestDto);

    /**
     * Replaces the title, description and duration of a part. Its position and its feedback are kept.
     *
     * @param programId             the program id
     * @param date                  the date of the day
     * @param partId                the part id
     * @param workoutPartRequestDto the new content of the part
     * @return the updated part
     * @throws NotFoundException if the program doesn't exist or belongs to another user, the date is a rest day, or
     *                           the part isn't part of this day
     */
    WorkoutPartResponseDto updateUserWorkoutPart(
            final Long programId,
            final LocalDate date,
            final Long partId,
            final WorkoutPartRequestDto workoutPartRequestDto);

    /**
     * Deletes a part and moves the parts after it up. Deleting the last part of a day also deletes the day.
     *
     * @param programId the program id
     * @param date      the date of the day
     * @param partId    the part id
     * @throws NotFoundException if the program doesn't exist or belongs to another user, the date is a rest day, or
     *                           the part isn't part of this day
     */
    void deleteUserWorkoutPart(final Long programId, final LocalDate date, final Long partId);

    /**
     * Sets the order of a day's parts to the order of the requested ids.
     *
     * @param programId                       the program id
     * @param date                            the date of the day
     * @param reorderWorkoutPartsRequestDto the part ids in the wanted order
     * @return the day with its parts in the new order
     * @throws BadRequestException if the ids aren't exactly the ids of the day's parts
     * @throws NotFoundException   if the program doesn't exist or belongs to another user, or the date is a rest day
     */
    ProgramDayResponseDto reorderUserWorkoutParts(
            final Long programId,
            final LocalDate date,
            final ReorderWorkoutPartsRequestDto reorderWorkoutPartsRequestDto);
}
