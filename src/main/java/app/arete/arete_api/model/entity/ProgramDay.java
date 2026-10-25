package app.arete.arete_api.model.entity;

import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.NotFoundException;
import app.arete.arete_api.util.Constants;
import app.arete.arete_api.util.enums.DayStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Table(
        name = "program_day",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_program_day_program_date",
                columnNames = {"program_id", "day_date"})
)
@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class ProgramDay extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayStatus status;

    @Column(length = 2000)
    private String notes;

    // Days are loaded by date range or page; batching loads their parts in one query instead of one per day
    @Builder.Default
    @BatchSize(size = Constants.MAX_PAGE_SIZE)
    @OneToMany(mappedBy = "programDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private Set<WorkoutPart> parts = new LinkedHashSet<>();

    /**
     * Adds the parts after the existing ones, in the given order.
     */
    public void appendParts(final List<WorkoutPart> orderedParts) {
        int orderIndex = this.parts.size();
        for (final WorkoutPart part : orderedParts) {
            part.setProgramDay(this);
            part.setOrderIndex(orderIndex++);
            this.parts.add(part);
        }
    }

    /**
     * Adds one part after the existing ones.
     */
    public void addPart(final WorkoutPart part) {
        appendParts(List.of(part));
    }

    /**
     * Replaces the planned content of a part; its position and feedback are kept.
     */
    public WorkoutPart updatePart(
            final Long partId,
            final String title,
            final String description,
            final Integer durationMinutes) {
        final WorkoutPart part = getPart(partId);
        part.setTitle(title);
        part.setDescription(description);
        part.setDurationMinutes(durationMinutes);
        return part;
    }

    /**
     * Removes a part and closes the gap it leaves in the order.
     */
    public void removePart(final Long partId) {
        final WorkoutPart part = getPart(partId);
        this.parts.remove(part);
        int orderIndex = 0;
        for (final WorkoutPart remainingPart : this.parts) {
            remainingPart.setOrderIndex(orderIndex++);
        }
    }

    /**
     * Orders the parts as the given ids; the ids must be exactly the ids of this day's parts.
     */
    public void reorderParts(final List<Long> orderedPartIds) {
        final Map<Long, WorkoutPart> partById = this.parts.stream()
                .collect(Collectors.toMap(WorkoutPart::getId, Function.identity()));
        if (orderedPartIds.size() != partById.size() || !partById.keySet().equals(Set.copyOf(orderedPartIds))) {
            throw new BadRequestException("The part ids must list every part of this day exactly once");
        }
        final List<WorkoutPart> orderedParts = orderedPartIds.stream().map(partById::get).toList();
        this.parts.clear();
        this.parts.addAll(orderedParts);
        int orderIndex = 0;
        for (final WorkoutPart orderedPart : orderedParts) {
            orderedPart.setOrderIndex(orderIndex++);
        }
    }

    public void giveFeedback(final DayStatus status, final String notes) {
        this.status = status;
        this.notes = notes;
    }

    public void givePartFeedback(final Long partId, final boolean completed, final String resultNotes) {
        final WorkoutPart part = findPart(partId)
                .orElseThrow(() -> new BadRequestException("Workout part %d isn't part of this day".formatted(partId)));
        part.giveFeedback(completed, resultNotes);
    }

    /**
     * Moves the day to another date and starts it again as planned, without any feedback.
     */
    public void moveTo(final LocalDate newDate) {
        this.dayDate = newDate;
        giveFeedback(DayStatus.PLANNED, null);
        this.parts.forEach(WorkoutPart::clearFeedback);
    }

    private WorkoutPart getPart(final Long partId) {
        return findPart(partId)
                .orElseThrow(() -> new NotFoundException("Workout part %d not found on this day".formatted(partId)));
    }

    private Optional<WorkoutPart> findPart(final Long partId) {
        return this.parts.stream()
                .filter(candidate -> candidate.getId().equals(partId))
                .findFirst();
    }
}
