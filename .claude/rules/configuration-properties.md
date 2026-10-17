---
paths:
  - "**/*.java"
  - "**/application*.yaml"
  - "**/application*.yml"
---

# Configuration and `@ConfigurationProperties`

## Typed properties records only
All externalized configuration is bound into **`@ConfigurationProperties` records** in `config/properties/`, named `<Area>Properties`:

```java
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration accessExpiration,
        @NotNull Duration refreshExpiration,
        boolean cookieSecure,
        boolean cookieHttpOnly,
        @NotBlank String cookiePath
) {
}
```

- Register them with `@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})` on the application class, or use `@ConfigurationPropertiesScan`.
- **Don't use `@Value`**, and don't read `Environment` directly in business code.
- Inject the record into the bean that needs it (`private final JwtProperties jwtProperties`) and read its accessors.
- Group properties by feature (one record per prefix). Nest records for sub-groups instead of flattening them with long prefixes.

## Never bypass a property
If a value has a property, **always** read it from the properties record. A hard-coded duplicate (e.g. a token lifetime of `3600 * 1000L` in a filter while `jwt.access-expiration` exists) drifts silently.

## Units
- Times are `java.time.Duration` and sizes are `org.springframework.util.unit.DataSize`, never raw `int`/`long` with the unit in a comment.
- In YAML, write them with a unit: `access-expiration: 1h`, `refresh-expiration: 30d`, `max-upload: 10MB`.
- Convert explicitly at the boundary that needs a number: `toMillis()` for JWT expiry, `(int) toSeconds()` for `Cookie#setMaxAge`. Never feed one numeric property into APIs that expect different units.

## Validation
- Put `@Validated` on the record and constraints (`@NotBlank`, `@NotNull`, `@Positive`, `@NotEmpty`) on its components, so a misconfigured environment fails **at startup**, not on the first request.

## YAML
- Keys are kebab-case and match the record components (`cookie-http-only` ↔ `cookieHttpOnly`). Don't repeat the prefix inside component names (`jwt.jwt-cookie-secure` → `jwt.cookie-secure`).
- Secrets and environment-specific values come from environment variables: `secret: ${JWT_SECRET}`. Never commit real secrets or production defaults.
- Profile-specific overrides go in `application-<profile>.yaml` (e.g. `h2` for local in-memory runs), not in commented-out blocks.
- Comments in YAML must stay true. Prefer self-describing values (`1h`) over comments.
- When you add an environment variable, document it in the project's environment-variable table (README/CLAUDE.md).

## `@Configuration` classes
- `@Configuration` classes only declare `@Bean`s and framework setup (`SecurityConfig`, CORS, password encoder). They contain no business logic.
- Beans that need settings take the properties record as a `@Bean` method parameter.
- Delete empty `@Configuration` classes instead of keeping placeholders.
