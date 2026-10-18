package app.arete.arete_api.util;

import lombok.experimental.UtilityClass;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@UtilityClass
public class PaginationUtils {

    private static final String ID_PROPERTY = "id";

    /**
     * Sorts by the given property, then by id, so rows with equal values keep a stable order across pages.
     */
    public static PageRequest toPageRequest(
            final int page,
            final int size,
            final String sortProperty,
            final Sort.Direction direction) {
        final Sort sort = Sort.by(direction, sortProperty).and(Sort.by(direction, ID_PROPERTY));
        return PageRequest.of(page, size, sort);
    }
}
