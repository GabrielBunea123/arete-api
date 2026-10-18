package app.arete.arete_api.specification;

import app.arete.arete_api.model.dto.program.ProgramDayFilterDto;
import app.arete.arete_api.model.entity.ProgramDay;
import app.arete.arete_api.util.enums.DayStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ProgramDaySpecifications {

    private static final String DAY_DATE = "dayDate";

    public Specification<ProgramDay> buildFilters(final Long programId, final ProgramDayFilterDto programDayFilterDto) {
        return Specification.allOf(
                belongsTo(programId),
                onOrAfter(programDayFilterDto.from()),
                onOrBefore(programDayFilterDto.to()),
                hasStatus(programDayFilterDto.status()));
    }

    private static Specification<ProgramDay> belongsTo(final Long programId) {
        return (root, query, cb) -> cb.equal(root.get("program").get("id"), programId);
    }

    private static Specification<ProgramDay> onOrAfter(final LocalDate from) {
        if (from == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(DAY_DATE), from);
    }

    private static Specification<ProgramDay> onOrBefore(final LocalDate to) {
        if (to == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get(DAY_DATE), to);
    }

    private static Specification<ProgramDay> hasStatus(final DayStatus status) {
        if (status == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
