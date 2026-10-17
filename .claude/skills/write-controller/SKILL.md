---
name: write-controller
description: Conventions for writing Spring Boot REST controllers in an OpenAPI-first project — defining the operation in openapi.yaml, implementing the generated *Api interface, endpoint paths and naming, generated request/response DTOs, pagination and filter params, authenticated-user ("/me") endpoints, response status codes, and security wiring (JWT, roles, premium). Use when adding a controller, adding or changing an endpoint, or reviewing controller code in a layered Spring Boot API.
---

# Writing REST controllers

The project uses a layered architecture: `Controller → Service (interface + impl) → Repository`, with MapStruct mappers. The API is **OpenAPI-first**: `../api/openapi.yaml` is the contract, and `openapi-generator` (`interfaceOnly=true`, `useTags=true`, `modelNameSuffix=Dto`) generates one `<Tag>Api` interface per tag in `{basePackage}.api` and the DTO models in `{basePackage}.api.model`. `{basePackage}` means the project's root package (`app.arete.arete_api`). Before writing anything, check what the project already has (constants, path enums, spec schemas) and reuse it.

Related skills: `write-dto` (spec schemas), `write-service` (what the controller calls) and `exception-handling` (how errors become responses).

## 1. What a controller does

A controller is a thin HTTP layer that implements a generated interface. Each endpoint should:

1. Receive the params the generated signature already binds and validates.
2. Call **one** service method.
3. Wrap the result in a `ResponseEntity`.

Rules:
- Keep business logic, repository calls and entity access out of controllers. Signatures use only generated `*Dto` models (and plain params).
- Never convert entities to DTOs in a controller. The only mapper use allowed here is building an internal **request-side** record from query params (`programMapper.toProgramDayFilterDto(from, to)`).
- Don't catch exceptions. Services throw typed exceptions, and the global `@RestControllerAdvice` turns them into `ProblemDetail` responses.

## 2. Workflow: spec first

1. Add or change the operation in `openapi.yaml`: path, `operationId`, `tags`, params, request body, success response and every error response (§5).
2. Run `./mvnw compile`. The generated `<Tag>Api` interface changes, and any existing controller that no longer matches **fails to compile**. That's the drift check.
3. Implement or adjust the controller method, then the service and mapper it needs.
4. Commit the spec and the code together.

Never edit generated sources (`target/generated-sources/openapi`). They are rebuilt on every compile.

## 3. Class skeleton

```java
package {basePackage}.controller;

@RestController
@RequiredArgsConstructor
public class LibraryController implements LibraryApi {

    private final LibraryService libraryService;       // service INTERFACE, never *ServiceImpl
    private final ProgramMapper programMapper;          // only for request-side records built from query params

    @Override
    public ResponseEntity<List<ProgramResponseDto>> getPrograms() {
        return ResponseEntity.ok(libraryService.getUserPrograms());
    }

    @Override
    public ResponseEntity<ProgramDayResponseDto> getProgramDay(final Long programId, final LocalDate date) {
        return ResponseEntity.ok(libraryService.getUserProgramDay(programId, date));
    }
}
```

- One controller per spec tag: `<Tag>Controller implements <Tag>Api`.
- **No `@RequestMapping`, `@GetMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody` or `@Valid`** on the class or its methods. The generated interface already carries the mappings, bindings and validation, and repeating them risks overriding them with something that doesn't match the spec.
- **No springdoc annotations** (`@Operation`, `@ApiResponses`, `@Tag`). Documentation lives in the spec.
- Every method is `@Override`, and every parameter is `final`.
- Inject dependencies through Lombok `@RequiredArgsConstructor` with `private final` fields. Don't use `@Autowired`.

## 4. Endpoint conventions (in the spec)

### Paths
Spec paths have no global prefix. The `/api/v1` prefix comes from `server.servlet.context-path` and the spec's `servers` URL.

| Kind | Example |
|---|---|
| Collection | `GET /programs`, `POST /admin/users` |
| Single item | `GET /programs/{programId}` |
| Nested resource | `GET /programs/{programId}/days/{date}` |
| Current user | `GET /me` |
| Action (non-CRUD) | `POST /admin/entitlements/{entitlementId}/revoke`, `POST /auth/refresh` |

- Resource segments are plural, kebab-case nouns. Path params are named after the resource (`programId`, `entitlementId`), never a bare `id`.

### operationIds (generated method names)
`getXs`, `getX`, `createX`, `updateX`, `deleteX`, or a verb for actions (`revokeEntitlement`, `importFitrFiles`). The service method adds `User` when it works on the caller's own data (`getPrograms` → `libraryService.getUserPrograms()`).

### Parameters
- Request bodies are a `$ref` to a request schema (`UserLoginRequest`). The generated method takes `@Valid` + the `*Dto`, so constraints written in the spec run automatically.
- Query params with a fixed set of values reference an enum schema, so invalid values are rejected when binding.
- Optional query params come through as nullable wrappers (`Long programId`). Pass them to the service as they are. Never wrap them in `Optional`.

### Authenticated user
Generated signatures can't take `@AuthenticationPrincipal`, and the client never sends its own user id for "my" resources. The **service** resolves the caller through `UserService.getAuthenticatedUser()` and enforces ownership (see `write-service`). The controller just calls the `getUserX(...)` method.

## 5. Response status codes

| Operation | Spec success response | Controller returns |
|---|---|---|
| GET | `200` with the response schema | `ResponseEntity.ok(dto)` |
| POST that creates | `201` with the response schema | `ResponseEntity.status(HttpStatus.CREATED).body(dto)` |
| POST action or login/logout, no body | `204` | `ResponseEntity.noContent().build()` |
| PUT | `200` with the response schema | `ResponseEntity.ok(dto)` |
| DELETE | `204` | `ResponseEntity.noContent().build()` |

- The status the code returns **must match** the success code in the spec.
- Every operation lists its error responses in the spec as `$ref: '#/components/responses/<Name>'`, all using the `Problem` schema with `application/problem+json`:
  - `400` for anything that takes input
  - `401` for anything that isn't public
  - `403` for admin and premium endpoints
  - `404` for lookups by id
  - `409` where conflicts are possible
  - `429` on login
- Write a `summary` and `description` for this specific operation. Never copy them from another endpoint.

## 6. List endpoints: pagination and filtering

- Pagination and filter params are declared in the spec, with defaults (`default: 0`, `default: 20`) and bounds (`minimum`, `maximum`). They're never hard-coded in the controller.
- A paginated response has its own schema per resource (`ProgramPageResponse {content, page, size, totalElements, totalPages}`), because generics don't survive generation.
- Group filters into an internal `<X>FilterDto` record in `model.dto.<domain>`, built by the domain's MapStruct mapper in the controller. The service turns it into a JPA `Specification`.
- Small, fixed lists (programs, blocks, days in a date range) return an array without pagination.

## 7. Security wiring

Security is stateless JWT in HttpOnly cookies (`JwtAuthenticationFilter`), with roles `ADMIN > USER` (role hierarchy) and a premium gate, all configured in `SecurityConfig`.

1. Keep path patterns in enums instead of string literals: `ApiPath` for protected resources (`PROGRAMS("/programs/**")`, `ADMIN("/admin/**")`) and `Endpoints` for public ones (`/auth/**`, `/actuator/health`). Patterns are relative to the context path.
2. Access levels:
   - public: `Endpoints` → `permitAll()`
   - signed in: `/me` → `authenticated()`
   - admin: `ApiPath.ADMIN` → `hasRole(RoleEnum.ADMIN.getValue())`
   - premium: everything else goes through `PremiumAuthorizationManager`, which checks for an active entitlement in the DB on every request
3. Grant the **lowest** level that should have access. The hierarchy covers the roles above it.
4. The pattern must match the real spec path, including nested paths (`/programs/*/days/**`). A mismatched pattern silently falls through to the next (usually weaker) rule.
5. State-changing requests need the CSRF header (`CookieCsrfTokenRepository`). Remember this when writing tests and clients.
6. Use `@PreAuthorize` for rules that path patterns can't express.

## 8. Checklist for a new controller or endpoint

- [ ] Operation added to `openapi.yaml`, with request/response schemas (`write-dto`) and every error response
- [ ] `./mvnw compile` regenerates the `<Tag>Api` interface
- [ ] Service interface + impl method (`write-service`)
- [ ] MapStruct mapper method for the response DTO (`write-mapper`)
- [ ] Controller `implements <Tag>Api`, with an `@Override` method that calls one service method and returns the spec's status
- [ ] `ApiPath`/`Endpoints` entry + `SecurityConfig` matcher, if it's a new path area
- [ ] MockMvc test for the success status and the main error statuses

## 9. Anti-patterns

- Entities in controller signatures, or repository access from a controller.
- Business logic, ownership checks or entity↔DTO mapping in a controller.
- Mapping or springdoc annotations on the controller, or endpoints that exist only in code and not in the spec.
- Editing generated sources, or hand-writing an API DTO that the spec should define.
- A returned status that differs from the spec (e.g. `ok()` on an operation documented as 201).
- Injecting `*ServiceImpl`, or accepting a `userId` for "my" operations.
- Hard-coded default values or path strings that are already defined in the spec, constants or enums.
