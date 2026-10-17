# Arete API: Backend Implementation Plan

Scope: the Spring Boot backend (`arete-api`) for the **foundation** plus **Phase 1 (Personal library)**. The frontend, nginx, Cloudflare and server setup are out of scope.

Stack: Java 25, Spring Boot 4.1.1 (keep the skeleton, and change "Java 21" in the tech spec), Maven, PostgreSQL, Flyway, Spring Data JPA, MapStruct, Spring Security with stateless JWT, OpenAPI-first.

Data source: raw fitr JSON in `api2md/exports/fitr/_raw_json`. It has 334 day files (224 *Compete - English*, 110 *Podium Chaser - English*). It also has 25 non-day files (`api_schedule__*.json`, which have `plans`/`mood` keys); the importer skips these.

## Conventions
The rules in `.claude/rules/` and the skills in `.claude/skills/` are the source of truth for how code is written, and this plan doesn't repeat them. Before writing or changing a layer, load the matching skill: `write-controller`, `write-service`, `write-repository`, `write-entity`, `write-migration`, `write-dto`, `write-mapper`, `exception-handling`. The project-specific choices they build on:
- **OpenAPI-first:** `api/openapi.yaml` is the contract. Controllers implement the generated `*Api` interfaces, and API DTOs are the generated `*Dto` models. Hand-written records are only for internal carriers (filters, fitr JSON, import results).
- **Errors:** RFC 9457 `ProblemDetail` (`application/problem+json`), built by one `GlobalExceptionHandler`.
- **Auth:** stateless JWT in HttpOnly cookies, with the premium check against the DB on every request.
- **Layering:** Controller → Service (interface + `*ServiceImpl`) → Repository, with MapStruct as the only entity ↔ DTO conversion.

---

## 0. Project setup
- [ ] `git init` at `arete-training-app/`. Put the contract at `arete-training-app/api/openapi.yaml`, a sibling of `arete-api/`.
- [ ] Add `arete-api/compose.dev.yml` with a local `postgres:17` service and a named volume.
- [ ] Replace `application.properties` with `application.yaml` (shared settings), `application-dev.yaml` (local DB) and `application-prod.yaml` (everything from env vars). Keys are kebab-case and bound to `@ConfigurationProperties` records (§4). No `@Value`.
- [ ] Set `server.servlet.context-path: /api/v1`. The spec's `servers` URL is `/api/v1`, and spec paths have no prefix (`/programs`).
- [ ] Package layout under `app.arete.arete_api`, by layer:
  - `config` (`SecurityConfig`, `JpaConfig`) and `config.properties` (`*Properties` records)
  - `controller`, `service`, `service.implementations`, `repository`, `mapper`
  - `model.entity`, and `model.dto.<domain>` for hand-written internal records only
  - `security` (`JwtService`, `JwtAuthenticationFilter`, `PremiumAuthorizationManager`, rate limiter)
  - `exception`, `validation`, `util` (`Constants`), `util.enums`
  - generated code: `api` (interfaces) and `api.model` (`*Dto`), under `target/`, never committed
- [ ] Add `CLAUDE.md` with the environment-variable table (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_COOKIE_SECURE`, `ARETE_ADMIN_EMAIL`, `ARETE_ADMIN_PASSWORD`) and a "Current highest migration" line, which `write-migration` keeps up to date.

## 1. Dependencies (`pom.xml`)
- [ ] `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `org.postgresql:postgresql`
- [ ] `flyway-core` + `flyway-database-postgresql`
- [ ] `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson` (runtime)
- [ ] `org.mapstruct:mapstruct`, with `mapstruct-processor` and `lombok-mapstruct-binding` in `maven-compiler-plugin` → `annotationProcessorPaths`, listed **after** `lombok`
- [ ] `spring-boot-starter-actuator`
- [ ] `openapi-generator-maven-plugin`:
  - `generatorName=spring`, `interfaceOnly=true`, `useTags=true`, `useSpringBoot3=true` (check for a Boot 4 flag), `useJakartaEe=true`, `openApiNullable=false`, `skipDefaultInterface=true`
  - `useBeanValidation=true` and `performBeanValidation=true`, so spec constraints become bean-validation annotations
  - `modelNameSuffix=Dto`, so schema `ProgramResponse` becomes `ProgramResponseDto`
  - `generatedConstructorWithRequiredArgs=false`, so MapStruct builds models through setters
  - `inputSpec=${project.basedir}/../api/openapi.yaml`, output to `target/generated-sources/openapi` (not committed)
  - `apiPackage=app.arete.arete_api.api`, `modelPackage=app.arete.arete_api.api.model`
- [ ] Test dependencies: `spring-boot-testcontainers`, `testcontainers-postgresql`, `junit-jupiter` (security-test is already present)
- [ ] Leave Spring AI out until Phase 3.

## 2. API contract (`api/openapi.yaml`)
OpenAPI 3.1, with `servers: [{url: /api/v1}]`. Every error response uses one `Problem` schema (RFC 9457, `application/problem+json`).

| Tag | Endpoints | Access |
|---|---|---|
| auth | `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/csrf` | public |
| me | `GET /me` → email, roles, `premium`, entitlement `validUntil` | signed in |
| admin | `GET /admin/users`, `GET/POST /admin/users/{userId}/entitlements`, `POST /admin/entitlements/{entitlementId}/revoke` | admin |
| import | `POST /admin/imports/fitr` (multipart: JSON files or a zip) → `ImportReportResponse {created, updated, skipped[{file, reason}]}` | admin |
| library | `GET /programs`, `GET /programs/{programId}/days?from&to` (summaries), `GET /programs/{programId}/days/{date}` (full day), `GET /programs/{programId}/blocks` | premium |
| today | `GET /today?programId=&date=` → `TodaySessionResponse {programId, sourceDate, fallbackYears, day, blockPosition{weekInBlock, weeksInBlock, phase}}` | premium |

Schema rules (see `write-dto`):
- Name schemas by DTO kind: `UserLoginRequest`, `EntitlementRequest`, `ProgramResponse`, `ProgramDaySummaryResponse`, `ProgramDayResponse`, `ProgramBlockResponse`, `TodaySessionResponse`, `MeResponse`. The generator adds the `Dto` suffix.
- Write validation as spec constraints (`required`, `maxLength`, `format: email`, `minimum`). Request schemas reference related data by id and never carry server-controlled fields.
- Enum values are UPPER_CASE (`BUILD`, `PEAK`, `TRANSITION`), so MapStruct maps them by name to `util.enums`.
- Each operation documents its success code and every error it can return (400/401/403/404/409/429) with `Problem`.

`date` on `/today` defaults to the server's current date. Accepting it as a parameter makes the endpoint testable.

Workflow for every change: edit the spec, run `./mvnw compile`, fix the controllers and mappers, and commit the spec and code together.

## 3. Database (Flyway, `src/main/resources/db/migration`)
Follow `write-migration` and `write-entity`:
- every `Auditable` table has `created_at`, `updated_at` and `version`
- ids are `BIGSERIAL`, and enum columns are `VARCHAR(255)`
- constraints are named (`fk_`, `uq_`, `chk_`, `idx_`), each FK has a deliberate `ON DELETE`, and every FK column is indexed

**V1__create_users_and_entitlements.sql**
- `users(id, email citext, password_hash, default_program_id null, audit columns)`, with `uq_users_email`. The name follows the skill's reserved-word rule.
- `user_role(user_id, role VARCHAR(255))` with PK `(user_id, role)`. It maps to an `@ElementCollection` of `RoleEnum` (`USER`, `ADMIN`) and cascades on user delete.
- `entitlement(id, user_id, plan, source, valid_from, valid_until null, revoked_at null, audit columns)`, with `plan` as `EntitlementPlan`, `source` as `EntitlementSource`, and an index on `(user_id, valid_from, valid_until)`
- `refresh_token(id, user_id, token_hash, expires_at, revoked_at null, created_at)`, with `uq_refresh_token_token_hash`, so logout and revocation work with stateless access tokens

**V2__create_library.sql**
- `program(id, user_id, source, external_id, title, color, audit columns)`, with `source` as `ProgramSource` (`FITR`), unique `(user_id, source, external_id)` and `ON DELETE CASCADE` from `users`
- `program_day(id, program_id, external_id, date, week_position, day_number, raw jsonb, imported_at, audit columns)`, unique `(program_id, date)`
- `program_section(id, day_id, external_id, order_index, kind, title, description text, attachments jsonb, extra jsonb)`:
  - `order_index` is filled from fitr's `position`
  - `kind` is `SectionKind` (`SECTION`, `OTHER`, `LEADERBOARD`, `CHALLENGE`, with `UNKNOWN` as the fallback)
  - `extra` holds `exercise`, `exercise_columns`, `benchmarks`, `performance` and `challenge` for later phases
  - it's an owned child of `program_day`, so there are no audit columns and it cascades on delete
- `program_block(id, program_id, start_date, weeks int default 4, source, audit columns)`, with `source` as `BlockSource` (`DETECTED`, `MANUAL`) and unique `(program_id, start_date)`
- jsonb columns map with `@JdbcTypeCode(SqlTypes.JSON)`.

Rules:
- Every row reaches a `user_id`. Library data is only read through owner-scoped finders (`findByIdAndUserId`, `findByProgramIdAndDateAndProgramUserId`, …). There is no unscoped `findById` on library data, and another user's data gets the same 404 as missing data.
- The library is read-only through the API. The only way to write it is re-import, an idempotent upsert keyed on fitr ids.
- `spring.jpa.hibernate.ddl-auto=validate`. The schema changes only through new migrations, and applied ones are never edited.

## 4. Cross-cutting
- [ ] **Errors (`exception-handling`)**:
  - `GlobalExceptionHandler` (`@RestControllerAdvice extends ResponseEntityExceptionHandler`) returns `ProblemDetail` for everything, including framework 400s
  - typed exceptions: `NotFoundException`, `BadRequestException`, `ConflictException`, `ForbiddenException`, `UnauthorizedException` and `TooManyRequestsException` (429)
- [ ] **Entities (`write-entity`)**:
  - an `Auditable` base class, with `@EnableJpaAuditing` on `JpaConfig`
  - entities use `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`, with no `@Data`
  - relations are LAZY, and every collection has `@Builder.Default`
  - entities never leave services
- [ ] **Mappers (`write-mapper`)**:
  - a `CentralMapperConfig` (`componentModel = "spring"`, `unmappedTargetPolicy = ERROR`)
  - `UserMapper`, `EntitlementMapper`, `ProgramMapper`, `ProgramDayMapper`, `ProgramBlockMapper`, `TodaySessionMapper`, `ImportMapper`
  - a shared `DateTimeMapper` for `LocalDateTime` ↔ `OffsetDateTime` (UTC)
  - they map entities to the generated `*Dto` models
- [ ] **Enums (`util.enums`)**: `RoleEnum`, `EntitlementPlan`, `EntitlementSource`, `ProgramSource`, `SectionKind`, `BlockSource`, `BlockPhase`, `ApiPath`, `Endpoints`
- [ ] **Constants (`util.Constants`)**: `ROLE_PREFIX`, the cookie names (`ACCESS_TOKEN`, `REFRESH_TOKEN`), the CSRF header name and `GENERIC_ERROR_DETAIL`
- [ ] **Properties (`config.properties`, all `@Validated` records)**:
  - `JwtProperties(secret, accessExpiration: 15m, refreshExpiration: 30d, cookieSecure, cookieHttpOnly, cookiePath)`
  - `AdminProperties(email, password)`
  - `LoginRateLimitProperties(maxAttempts: 5, window: 15m)`
  - `ImportProperties(importDir)`
  - `BlockProperties(defaultWeeks: 4)`
- [ ] `UserService.getAuthenticatedUser()` resolves the caller: SecurityContext → `users` by email, throwing `UnauthorizedException` if the user is missing. Other services depend on it, and it's a leaf.

## 5. Security and entitlements (`config.SecurityConfig`, `security`)
- [ ] `SecurityFilterChain`:
  - `SessionCreationPolicy.STATELESS`, `DaoAuthenticationProvider` with `BCryptPasswordEncoder`
  - `JwtAuthenticationFilter` (`OncePerRequestFilter`) reads the access-token cookie, validates it with `JwtService` and sets the context. Failures go through `HandlerExceptionResolver` as `UnauthorizedException` and become a 401 Problem.
  - the authentication entry point and access-denied handler also delegate to `HandlerExceptionResolver`, so 401 and 403 are Problem bodies, never redirects
  - CSRF via `CookieCsrfTokenRepository.withHttpOnlyFalse()` and `CsrfTokenRequestAttributeHandler` (SPA pattern). It stays on because the auth cookies are sent automatically. `GET /auth/csrf` issues the token.
  - cookies are `HttpOnly`, `SameSite=Lax`, and `Secure` per `JwtProperties`
- [ ] Auth endpoints (`AuthService` / `AuthServiceImpl`):
  - `login`: `AuthenticationManager.authenticate`, then issue the access JWT cookie and a refresh-token cookie (path `/api/v1/auth/refresh`). The refresh token is stored hashed in `refresh_token`. Returns 204, or a 401 Problem on bad credentials.
  - `refresh`: validate the stored token, rotate it (revoke the old one, issue a new one) and issue a new access token
  - `logout`: revoke the refresh token and clear both cookies
- [ ] Authorization rules, with patterns from `ApiPath`/`Endpoints` and role names from `RoleEnum`:
  - `Endpoints` (`/auth/**`, `/actuator/health`): permitAll
  - `/me`: authenticated
  - `/admin/**`: `hasRole(ADMIN)`, with a role hierarchy `ADMIN > USER`
  - everything else goes through `PremiumAuthorizationManager`, which calls `entitlementRepository.hasActiveEntitlement(userId)` on **every request**. Temporal validity is checked in JPQL, so an expired or revoked entitlement blocks access immediately without logging the user out. Role changes take effect when the access token expires.
- [ ] Login rate limit: allows `LoginRateLimitProperties.maxAttempts` per `window` per email+IP (an in-memory map is fine on one instance) and throws `TooManyRequestsException` (429 Problem)
- [ ] `AdminBootstrap` (`ApplicationRunner`): if no users exist, create the admin from `AdminProperties` with a manual entitlement that has no end date
- [ ] `EntitlementService` / `EntitlementServiceImpl`: `getUserEntitlements`, `grantEntitlement`, `revokeEntitlement`, behind the admin controller. A future payment webhook will call the same `grantEntitlement`.

## 6. fitr importer
- [ ] Internal records in `model.dto.fitr` that mirror the raw JSON, with `@JsonIgnoreProperties(ignoreUnknown = true)`:
  - `FitrDay(id, date, plan{id, title}, color, day{number, week{position}, sections[]})`
  - `FitrSection(id, kind, title, description, position, attachments, exercise, exercise_columns, benchmarks, performance, challenge)`
- [ ] `FitrImportService` / `FitrImportServiceImpl`:
  - `importFitrFiles(Stream<NamedInput>)` is called by the endpoint and imports into the caller's library (`getAuthenticatedUser()`)
  - `importFitrFilesForUser(User, Stream<NamedInput>)` is called by the dev runner, which has no authenticated caller
  - both are `@Transactional`: one transaction per upload

  For each file:
  - parse it; if `plan` or `day` is missing, skip it with the reason "not a day export"
  - upsert `program` by `plan.id` (title comes from the plan title, e.g. "Compete - English")
  - upsert `program_day` by `(program, date)` and store the full file in `raw`
  - replace its sections through the `ProgramDay.replaceSections(...)` domain method (`cascade = ALL`, `orphanRemoval = true`, `@OrderBy("orderIndex ASC")`). `description` is plain text, `formatted_description` HTML is ignored, and an unknown `kind` maps to `SectionKind.UNKNOWN`.
  - return an `ImportReport` (internal record in `model.dto.importer`), which `ImportMapper` maps to `ImportReportResponseDto`
- [ ] `BlockDetectionService` / `BlockDetectionServiceImpl` runs after each import, per program:
  - a day starts a block when a section title matches `(?i)TRAINING BLOCK W1/(\d)`, or an attachment caption contains `\((\d{2})\.(\d{2})-(\d{2})\.(\d{2})\)` (start/end dates, e.g. "02.03-29.03"). Both patterns are `private static final Pattern`.
  - normalise each start to the Monday of its week
  - fill gaps between detected starts by stepping `BlockProperties.defaultWeeks`
  - never overwrite `BlockSource.MANUAL` rows
- [ ] Dev convenience: a `CommandLineRunner` in profile `dev` only, active when `arete.import.import-dir` (`ImportProperties`) is set, that feeds files from that directory into the same service for the admin user

## 7. Library and today
- [ ] `LibraryService` / `LibraryServiceImpl`:
  - `getUserPrograms()`
  - `getUserProgramDays(programId, from, to)`
  - `getUserProgramDay(programId, date)`, with sections ordered by `orderIndex`
  - `getUserProgramBlocks(programId)`
  - every method resolves the caller through `UserService.getAuthenticatedUser()` and uses owner-scoped finders
- [ ] `TodayService` / `TodayServiceImpl.getUserTodaySession(programId, date)`, where `programId` is a nullable `Long` (never `Optional`):
  1. the program comes from the parameter, then `users.default_program_id`, then the first program
  2. for `n = 1, 2, …` while a year `date.year - n` still has history: target = same **ISO week number and weekday** in that year (`date.with(IsoFields.WEEK_BASED_YEAR, y)`), and the first matching `program_day` wins
  3. if nothing matches, throw `NotFoundException("No session for this period")`, which becomes a 404 Problem
  4. `BlockPosition` comes from the `program_block` containing `sourceDate`:
     - `weekInBlock = weeks between block start and sourceDate + 1`
     - `phase = BlockPhase.forWeek(weekInBlock)`: `BUILD` for weeks 1–2, `PEAK` for week 3, `TRANSITION` for week 4
     - the mapping lives only on the enum, so Phase 2 can refine it
- [ ] Controllers `LibraryController implements LibraryApi` and `TodayController implements TodayApi`, like every other controller (`AuthController`, `MeController`, `AdminController`, `ImportController`). Each method calls one service method and wraps the result in a `ResponseEntity` whose status matches the spec.

## 8. Packaging
- [ ] `Dockerfile` (multi-stage: `eclipse-temurin:25-jdk`, `./mvnw package`, `eclipse-temurin:25-jre`) or `./mvnw spring-boot:build-image`
- [ ] Actuator exposes `health` only, at `/api/v1/actuator/health` (under the context path), for container healthchecks
- [ ] Flyway runs on startup, and `ddl-auto=validate`

## Order of work
1. Setup, dependencies, a minimal spec (`/me` plus `Problem`), the generator and MapStruct wired in, so `./mvnw compile` passes
2. Cross-cutting (§4), V1 migration, JWT security, admin bootstrap and entitlements, so login, refresh, `/me` and the premium gate work
3. V2 migration, importer and block detection, then a dev import of `_raw_json`
4. Library and today endpoints
5. Dockerfile, actuator and the prod profile

## Tests (`./mvnw verify`, Testcontainers Postgres)
- **Importer:** copy about 6 raw files plus one `api_schedule__*.json` into `src/test/resources/fitr/`. Check counts, that the skip reason is reported, that re-import leaves counts unchanged, and that sections are ordered.
- **BlockDetectionService:** a "W1/4" title and a "(02.03-29.03)" caption both detect a block, gaps get filled, and a manual block is kept.
- **TodayService:** an exact ISO-week match; a gap in last year falls back two years; no history returns 404; a transition-week position; a default program is chosen when none is given.
- **Security (MockMvc + `spring-security-test`):**
  - anonymous request: 401 Problem
  - non-premium user: 200 on `/me`, 403 on `/programs`
  - expired entitlement: 403 with the same access token
  - non-admin on `/admin/**`: 403
  - POST without CSRF: 403
  - 6th bad login: 429
  - refresh rotates the refresh token, and a revoked or logged-out token gets 401
  - every error body is `application/problem+json` with `status`, `title` and `detail`
- **Data isolation:** user B cannot read user A's program (404).
- **Contract drift:** a controller no longer compiles when its operation changes in the spec, which comes from the generator setup. A new DTO field nobody maps fails the build through `unmappedTargetPolicy = ERROR`.

## Manual check
`docker compose -f compose.dev.yml up -d`, then `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--arete.import.import-dir=../../api2md/exports/fitr/_raw_json`. Then use curl with a cookie jar (or an `.http` file):
1. `GET /api/v1/auth/csrf`, then `POST /api/v1/auth/login` with the `X-XSRF-TOKEN` header, which sets the access and refresh cookies
2. `GET /api/v1/me`
3. `GET /api/v1/programs`
4. `GET /api/v1/today?date=2027-03-08` (Monday, ISO week 10) should return the Monday of ISO week 10 in 2026 (2026-03-02, "NEW TRAINING BLOCK W1/4"), with `weekInBlock=1` and `phase=BUILD`. Compare it with `api2md/exports/fitr/Compete/2026-03-02.md`.

## Later phases (backend notes only)
- **Phase 2:** per-program style profile (weekly layout, section types, progressions, load percentages) stored as jsonb.
- **Phase 3:** brief and maxes tables, and `user_program` / `user_block` / `user_session` with `status` (`DRAFT`/`APPROVED` enum) and `based_on_section_id`. Spring AI with Claude generates the plan first, then the sessions.
- **Phase 4:** manual edits, regeneration with feedback, "show original", and `needs_update` flags.
- **Phase 5:** results and maxes logging; fitr `performance` is already kept in `program_section.extra`.
