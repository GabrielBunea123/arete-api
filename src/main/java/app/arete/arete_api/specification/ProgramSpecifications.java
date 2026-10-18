package app.arete.arete_api.specification;

import app.arete.arete_api.model.dto.program.ProgramFilterDto;
import app.arete.arete_api.model.entity.Program;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;

@Component
public class ProgramSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    public Specification<Program> buildFilters(final Long ownerId, final ProgramFilterDto programFilterDto) {
        return Specification.allOf(
                ownedBy(ownerId),
                nameContains(programFilterDto.name()),
                createdOnOrAfter(programFilterDto.createdFrom()),
                createdOnOrBefore(programFilterDto.createdTo()));
    }

    private static Specification<Program> ownedBy(final Long ownerId) {
        return (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
    }

    private static Specification<Program> nameContains(final String name) {
        if (name == null || name.isBlank()) {
            return Specification.unrestricted();
        }
        final String pattern = "%" + escapeLike(name.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE);
    }

    private static Specification<Program> createdOnOrAfter(final LocalDate createdFrom) {
        if (createdFrom == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom.atStartOfDay());
    }

    private static Specification<Program> createdOnOrBefore(final LocalDate createdTo) {
        if (createdTo == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), createdTo.plusDays(1).atStartOfDay());
    }

    private static String escapeLike(final String value) {
        return value
                .replace(String.valueOf(LIKE_ESCAPE), String.valueOf(LIKE_ESCAPE) + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }
}
