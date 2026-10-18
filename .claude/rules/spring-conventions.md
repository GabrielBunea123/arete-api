# Spring Boot project conventions

- **Layering:** Controller → Service (interface + `*ServiceImpl`) → Repository (Spring Data JPA). MapStruct mappers are the only place where entities ↔ DTOs are converted. Controllers never touch repositories or entities.
- **OpenAPI-first:** `../api/openapi.yaml` is the API contract. Controllers implement the generated `*Api` interfaces (`app.arete.arete_api.api`), and API DTOs are the generated `*Dto` models (`app.arete.arete_api.api.model`). Change the spec first, then run `./mvnw compile`. Hand-written records in `model.dto.<domain>` are only for internal carriers (filters, import formats, service results).
- **Use the matching skill** before creating or changing a layer: `write-controller`, `write-service`, `write-repository`, `write-entity`, `write-migration`, `write-dto`, `write-mapper`, `exception-handling`.
- **Dependency injection:** constructor injection only (Lombok `@RequiredArgsConstructor` + `private final`). Depend on interfaces, not `*Impl` classes.
- **Lists:** GET endpoints that return a list of resources are paginated (`Page`/`Size` params from `common.yaml`, an `XPageResponse` schema) and accept `sort` (a per-resource sort-field enum whose value is the entity property) + `direction` (`SortDirection` from `common.yaml`), plus the filters that make sense for the resource (an `XFilterDto` record + `XSpecifications`). Build the `PageRequest` with `PaginationUtils.toPageRequest`. Plain arrays only for views bounded by a single resource (a program's weekly schedule, a calendar date range).
- **Errors:** throw typed exceptions from services, and let the global `@RestControllerAdvice` build every error response as an RFC 9457 `ProblemDetail`.
- **Security:** stateless JWT in HttpOnly cookies. Services resolve the caller through `UserService.getAuthenticatedUser()`, and premium access is checked against the DB on every request.
- **Schema changes:** every entity change ships with a new Flyway migration `V{next}__{description}.sql` (see `write-migration`). Never edit a migration that has already been applied.
- **Config:** `@ConfigurationProperties` records, no `@Value`, no magic numbers (see `configuration-properties.md` and `constants-and-enums.md`).
- **Style:** `final` everywhere, descriptive names, enums over string flags (see `java-style.md`).
