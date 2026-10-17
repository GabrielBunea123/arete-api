package app.arete.arete_api.util.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleEnum {
    USER("USER"),
    ADMIN("ADMIN");

    private final String value;
}
