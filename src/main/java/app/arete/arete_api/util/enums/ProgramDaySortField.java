package app.arete.arete_api.util.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Fields a program day list can be sorted by; the value is the entity property.
 */
@Getter
@RequiredArgsConstructor
public enum ProgramDaySortField {
    DATE("dayDate");

    private final String value;
}
