---
paths:
  - "**/*.java"
---

# Constants and enums

## No magic values
Every literal that carries meaning gets a name. Where it lives depends on its scope:

| Scope | Where |
|---|---|
| Tuning values used by one class (weights, limits, factors) | `private static final` at the top of that class |
| App-wide values (defaults, cookie/header names, role prefix, shared bounds) | `util/Constants` (`@UtilityClass`) |
| Validation messages shared by constraints | `validation/ValidationMessages` (`@UtilityClass`) |
| Values that differ per environment (lifetimes, URLs, flags, secrets) | `@ConfigurationProperties` (see `configuration-properties.md`) |
| Closed sets of values | an enum in `util/enums` |

```java
// bad
final String token = jwtService.generateToken(user, 3600 * 1000L);
if (score > 10) { ... }

// good
final String token = jwtService.generateToken(user, jwtProperties.accessExpiration().toMillis());
if (score > Constants.MAX_SCORE) { ... }
```

- Obvious values (`0`, `1`, `-1`, `""`, loop bounds from `size()`) don't need names.
- Numeric constants document their unit in the name or type (`BASE_DURATION_SECONDS`, `Duration`).
- Use `@UtilityClass` for constant holders. Don't use interfaces or instantiable classes for them.

## Enums instead of strings and ints
- Any value from a fixed set (roles, statuses, types, visibility, purposes, sort direction, providers, API paths, public endpoints) is an **enum** in `util/enums`. It's never a `String`/`int` flag or a set of string constants.
- Use the enum type everywhere: DTO fields, `@RequestParam`s, entity fields (`@Enumerated(EnumType.STRING)`), method parameters. Convert to a string only at the boundary that needs it.
- Compare with `==` and branch with `switch` expressions. Never compare `name()` strings.
- Use `EnumSet` and `EnumMap` for collections keyed by an enum.

## Enum shape
```java
@Getter
@RequiredArgsConstructor
public enum Visibility {
    PUBLIC("public"),
    PRIVATE("private");

    private final String value;

    public static final String DEFAULT_VISIBILITY = "PRIVATE";   // compile-time constant for annotations
}
```

- Add a `private final String value` (via `@Getter @RequiredArgsConstructor`) only when the enum needs an external representation: URL patterns (`ApiPath`, `Endpoints`), role names for Spring Security (`RoleEnum`), display labels.
- Model hierarchies with an attribute (`private final Area parent`), and put lookups on the enum as methods (`isRoot()`, `children()`) rather than repeating `filter(a -> a.getParent() == null)` in services.
- Annotation attributes (`@RequestParam(defaultValue = ...)`) need compile-time `String` constants. Declare them on the enum as `public static final String DEFAULT_X = "CONSTANT_NAME"`, and make sure the string equals an enum constant name.
- Enum-to-value maps that configure behaviour (e.g. ratios per type) go in `Constants` as an immutable `Map.of(...)` keyed by the enum, or on the enum itself as an attribute.

## Security and routing strings
- Route patterns come only from path enums (`ApiPath.PRODUCTS.getValue()`, `Endpoints.LOGIN.getValue()`).
- Role names come only from `RoleEnum.X.getValue()`. Spring's `ROLE_` prefix comes from `Constants.ROLE_PREFIX` or the framework's default-prefix helpers.
- Header, cookie and claim names are constants (`Constants.ACCESS_TOKEN`, `Constants.AUTHORIZATION`, `Constants.BEARER`).
