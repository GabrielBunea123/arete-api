---
name: write-dto
description: Conventions for DTOs in an OpenAPI-first Spring Boot REST API — defining request/response/update/pagination schemas in openapi.yaml (generated as *Dto classes), naming, validation as spec constraints, enums, referencing related data by id, nested response schemas, the Problem error schema, and hand-written internal records (filters, import formats, service results) in model.dto.<domain>. Use when adding or changing an API schema, adding a field that crosses the API boundary, adding validation to input, or adding an internal record.
---

# Writing DTOs

DTOs are the API contract. Controllers only accept and return DTOs, and entities never cross the HTTP boundary. There are two kinds:

| Kind | Where it's defined | Where it ends up |
|---|---|---|
| **API DTOs** (request, response, pagination, error) | schemas in `../api/openapi.yaml` (`components.schemas`) | generated classes in `{basePackage}.api.model`, named `<Schema>Dto` (`modelNameSuffix=Dto`) |
| **Internal DTOs** (filters, import formats such as fitr JSON, service results) | hand-written Java records | `{basePackage}.model.dto.<domain>` (`fitr`, `library`, `importer`) |

Related skills: `write-controller` (the operations that use the schemas), `write-mapper` (entity↔DTO) and `exception-handling` (how validation failures are returned).

## 1. API DTOs are spec schemas

```yaml
components:
  schemas:
    EntitlementRequest:
      type: object
      required: [plan, validFrom]
      properties:
        plan:
          $ref: '#/components/schemas/EntitlementPlan'
        validFrom:
          type: string
          format: date
        validUntil:
          type: string
          format: date
          description: Null means the entitlement never expires.
    EntitlementPlan:
      type: string
      enum: [PREMIUM]
```

- **Never write an API DTO by hand, and never edit a generated one.** Change the schema, then run `./mvnw compile`.
- Generated models are mutable classes, not records. Treat them as immutable anyway: build them in a mapper, return them, and don't modify them afterwards.
- Enums are their own schema with UPPER_CASE values that match the domain enum in `{basePackage}.util.enums`, so MapStruct maps them by name. Reference them with `$ref`, never with a free-form `string`.
- Dates are `format: date` (`LocalDate`), timestamps `format: date-time` (`OffsetDateTime`), ids `type: integer, format: int64` (`Long`).

## 2. Schema kinds and naming

| Kind | Schema name | Generated class | Used for |
|---|---|---|---|
| Create request | `XRequest` | `XRequestDto` | `POST` body |
| Update request | `UpdateXRequest` | `UpdateXRequestDto` | `PUT` body when editable fields differ from create |
| Action request | `UserLoginRequest`, `ImportFitrRequest` | `...RequestDto` | Bodies of non-CRUD actions |
| Response | `XResponse` | `XResponseDto` | Everything returned to clients |
| Summary response | `XSummaryResponse` | `XSummaryResponseDto` | List items that are lighter than the full response |
| Pagination | `XPageResponse` | `XPageResponseDto` | One per paginated resource (§7) |
| Nested value | `BlockPosition`, `SkippedFile` | `BlockPositionDto` | Elements inside another schema |
| Error | `Problem` | `ProblemDto` | Every error response (RFC 9457) |

Use a separate response schema even when it matches the request field for field.

## 3. Request schemas: validation

Validation is written as spec constraints. With `useBeanValidation=true` the generator turns them into Jakarta annotations, and they run because the generated interface marks bodies `@Valid`. Failures become a single 400 Problem.

| Field type | Spec constraint | Generated annotation |
|---|---|---|
| Required field | listed in `required` | `@NotNull` |
| Non-empty string | `minLength: 1`, plus `maxLength: n` matching the column length | `@Size(min = 1, max = n)` |
| Email | `format: email` | `@Email` |
| Numbers with bounds | `minimum` / `maximum` | `@Min` / `@Max` (`@DecimalMin`/`@DecimalMax`) |
| Pattern | `pattern: '^...$'` | `@Pattern` |
| Non-empty array | `minItems: 1` | `@Size(min = 1)` |
| Nested object or array of objects | `$ref` | `@Valid` (added automatically) |

- `required` alone doesn't reject `""`. Add `minLength: 1` to required strings.
- Write a `description` on any field whose meaning or unit isn't obvious (`validUntil`, `weeks`).
- **Cross-field rules** (e.g. `validUntil` after `validFrom`) can't be expressed in the spec. Check them in the service and throw `BadRequestException`.
- Business-rule checks that need the database (existence, uniqueness, ownership) belong in the service.

## 4. What goes in, and what never does

**Request schemas**
- Reference related data **by id**: `programId`, `userId` in admin paths. Never accept nested resource objects.
- Never include server-controlled fields: `id`, the owner (it comes from the authenticated user), `createdAt`, `updatedAt`, `version`, computed values (block position, fallback years), flags the server decides (`source` on entitlements granted by an admin) or anything import-owned.
- Child items use a nested schema. The array order is the item order, so there's no `orderIndex` field.

**Response schemas**
- Include `id` and the fields clients need. Related data is either a **nested response schema** (`day: ProgramDayResponse`) or a flattened id (`programId`).
- Never expose password hashes, token hashes, refresh tokens, raw import payloads (`program_day.raw`), or `version` (unless the API deliberately uses it for concurrency).
- Don't nest schemas in both directions (parent → children → parent). A child that needs its parent carries `parentId`.
- Keep a stable property order: `id` first, then scalars, then nested objects and arrays.
- Mark always-present properties `required`, so clients can rely on them.

## 5. Booleans and naming

- Name boolean properties without an `is` prefix (`premium`, `featured`). The generator produces `getPremium()`/`isPremium()`, and MapStruct matches it to the entity property by name.
- Name properties in camelCase after the domain, not the storage (`sourceDate`, not `program_day_date`).

## 6. Internal records (`model.dto.<domain>`)

Hand-written records are for data that never appears in the spec:
- filters built from query params (`ProgramDayFilterDto`)
- external formats being parsed (`FitrDay`, `FitrSection`, with `@JsonIgnoreProperties(ignoreUnknown = true)`)
- values passed between services (`ImportReport`, `BlockPosition` before mapping)

```java
package {basePackage}.model.dto.library;

public record ProgramDayFilterDto(
        LocalDate from,
        LocalDate to
) {
}
```

- They are immutable `record`s with no Lombok, setters or behaviour beyond normalization. Use one record per file, with components one per line.
- Filter records contain only optional criteria as nullable types, with no validation annotations. Null means "don't filter". Component names match the query param names, because the mapper maps them by name.
- Use a compact constructor only for normalization (non-null collections, defaults), never for validation or business logic:
  ```java
  public record ImportReport(int created, int updated, List<SkippedFile> skipped) {
      public ImportReport {
          skipped = skipped == null ? List.of() : List.copyOf(skipped);
      }
  }
  ```
- If an internal record becomes part of the API, define a schema for it and map the record to the generated DTO. Don't return the record from a controller.

## 7. Pagination

Generics don't survive generation, so each paginated resource gets its own page schema:

```yaml
ProgramDayPageResponse:
  type: object
  required: [content, page, size, totalElements, totalPages]
  properties:
    content: { type: array, items: { $ref: '#/components/schemas/ProgramDaySummaryResponse' } }
    page: { type: integer }
    size: { type: integer }
    totalElements: { type: integer, format: int64 }
    totalPages: { type: integer }
```

Page and size are query params with defaults and bounds in the spec. Small bounded lists (programs, blocks, days in a date range) return a plain array instead.

## 8. Checklist

- [ ] API DTO: schema in `openapi.yaml` named with the right kind (§2), `./mvnw compile` run, and nothing hand-written in `api.model`
- [ ] Request fields constrained (`required`, `minLength`/`maxLength`, `format`, bounds), and cross-field rules checked in the service
- [ ] Enums as UPPER_CASE schema enums that match `util.enums`
- [ ] Relations by id in requests, nested schemas or flattened ids in responses
- [ ] No server-controlled or sensitive fields
- [ ] Booleans without `is` prefix
- [ ] Internal records only in `model.dto.<domain>`, never returned from a controller
- [ ] Mapper methods added or updated (`write-mapper`)
