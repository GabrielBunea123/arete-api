package app.arete.arete_api.util.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SameSite {
    STRICT("Strict"),
    LAX("Lax"),
    NONE("None");

    private final String value;
}
