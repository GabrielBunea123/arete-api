---
name: exception-handling
description: Conventions for error handling in a Spring Boot REST API — typed unchecked exceptions per HTTP status, a single @RestControllerAdvice producing RFC 9457 ProblemDetail bodies (application/problem+json), bean-validation and framework errors, errors raised inside security filters, entry points and access-denied handlers, and documenting errors in openapi.yaml. Use when throwing an exception from a service, adding an exception type or handler, handling validation failures, or debugging a wrong error status or body.
---

# Exception handling

The model is simple: **services throw a typed unchecked exception with a client-readable message, and one global `@RestControllerAdvice` turns it into an RFC 9457 `ProblemDetail` with the matching HTTP status.** Nothing else in the application catches or formats errors.

Everything lives in `{basePackage}.exception`. If the project doesn't have these classes yet, create them exactly as shown.

## 1. Exception classes

| Exception | Extends | Status | Throw when |
|---|---|---|---|
| `NotFoundException` | `RuntimeException` | 404 | An entity lookup failed, or a resource isn't owned by the caller (see §4) |
| `BadRequestException` | `RuntimeException` | 400 | Input is well-formed but semantically invalid, and the spec's constraints can't express the rule |
| `ConflictException` | `RuntimeException` | 409 | State conflict: unique value taken, duplicate, delete blocked by references |
| `ForbiddenException` | `RuntimeException` | 403 | The authenticated caller may not perform this action |
| `UnauthorizedException` | `RuntimeException` | 401 | Missing, invalid or expired authentication (JWT filter, refresh, login) |
| `TooManyRequestsException` | `RuntimeException` | 429 | A rate limit was hit (login attempts) |

Every class has the same shape: one constructor taking the message, and no extra fields.

```java
package {basePackage}.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(final String message) {
        super(message);
    }
}
```

Only unchecked exceptions are used. Don't declare `throws` on service methods, and don't create checked exceptions.

## 2. Throwing

Throw from **services**, or from entity domain methods that protect an invariant (e.g. `program.addBlock(...)` throwing `ConflictException("A block already starts on this date")`). Never throw from controllers, and never catch these in the request path.

```java
// Lookup, scoped to the caller
final Program program = programRepository.findByIdAndUserId(programId, user.getId())
        .orElseThrow(() -> new NotFoundException(String.format("Program with id %d not found", programId)));

// Uniqueness
if (userRepository.existsByEmail(email)) {
    throw new ConflictException("The email is already in use");
}

// Cross-field rule the spec can't express
if (request.getValidUntil() != null && request.getValidUntil().isBefore(request.getValidFrom())) {
    throw new BadRequestException("validUntil must not be before validFrom");
}
```

Message rules:
- The message becomes the Problem's `detail` and goes **straight to the client**. Keep it short and specific, with no stack traces, SQL or internal class names.
- Include the offending id when it helps.
- Don't put secrets or other users' data in messages. Login failures say "Invalid email or password", never which part was wrong.
- Always pass a supplier to `orElseThrow`. A bare `.orElseThrow()` throws `NoSuchElementException`, which falls into the catch-all as a **500**.

## 3. GlobalExceptionHandler and ProblemDetail

`GlobalExceptionHandler` is a `@RestControllerAdvice` with `@Slf4j` that **extends `ResponseEntityExceptionHandler`**. The base class already turns Spring MVC's own exceptions into `ProblemDetail` with the right status:
- `MethodArgumentNotValidException`
- `HandlerMethodValidationException`
- `HttpMessageNotReadableException`
- `MissingServletRequestParameterException`
- `HttpRequestMethodNotSupportedException`
- `NoResourceFoundException`
- …

Add one `@ExceptionHandler` per typed exception:

| Handler | Catches | Status |
|---|---|---|
| `handleNotFoundException` | `NotFoundException` | 404 |
| `handleConflictException` | `ConflictException` | 409 |
| `handleBadRequestException` | `BadRequestException`, `MethodArgumentTypeMismatchException`, `ConstraintViolationException` | 400 |
| `handleUnauthorizedException` | `UnauthorizedException`, Spring `AuthenticationException` | 401 |
| `handleForbiddenException` | `ForbiddenException`, Spring `AccessDeniedException` | 403 |
| `handleTooManyRequestsException` | `TooManyRequestsException` | 429 |
| `handleException` | `Exception` (catch-all) | 500, generic detail, logged |

Each handler has the same body. It builds a `ProblemDetail`, and Spring writes it as `application/problem+json` and fills in `instance` with the request path:

```java
@ExceptionHandler(NotFoundException.class)
public ProblemDetail handleNotFoundException(final NotFoundException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
}
```

The catch-all **never** returns `exception.getMessage()`. It logs the real exception and returns a generic detail:

```java
@ExceptionHandler(Exception.class)
public ProblemDetail handleException(final Exception exception) {
    log.error("Unhandled exception", exception);
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, Constants.GENERIC_ERROR_DETAIL);
}
```

Every error the client sees therefore looks like this:
```json
{ "type": "about:blank", "title": "Not Found", "status": 404, "detail": "Program with id 5 not found", "instance": "/api/v1/programs/5" }
```

- `type` stays `about:blank` and `title` is the status reason phrase, unless the project introduces problem-type URIs. If it does, keep them as constants.
- Extra members (e.g. validation `errors`) go through `problemDetail.setProperty(...)`, never through a separate body class.
- To map a framework exception to an existing status, add it to that handler's `value = {...}` list instead of creating a new handler.
- The spec's `Problem` schema (`type`, `title`, `status`, `detail`, `instance`, optional `errors`) must match this shape. Every operation's error responses reference it (see `write-controller` §5).

## 4. Choosing the status

- **Missing entity → 404.** Use this for path ids and for ids referenced inside request bodies.
- **Exists but belongs to someone else → 404.** Look it up scoped to the owner (`findByIdAndUserId(id, userId)`) and throw `NotFoundException`. This doesn't reveal that other users' resources exist.
- If you need to say "exists but not yours" explicitly, throw **`ForbiddenException` (403)**. Never use `ConflictException` for authorization.
- **Duplicate / already exists / blocked by references → 409.**
- **Invalid input → 400.** Prefer spec constraints (see `write-dto` §3). Use `BadRequestException` for rules that need service or DB context, or that span several fields.
- **401 vs 403.** 401 means "who are you?" (no, invalid or expired token). 403 means "known caller, not allowed": not an admin, no active premium entitlement, or a missing CSRF token.
- **Rate limited → 429.**

## 5. Validation errors

Request DTOs are generated from the spec with Jakarta Bean Validation annotations, and the generated interface marks bodies and params for validation. Failures raise `MethodArgumentNotValidException` (bodies) or `HandlerMethodValidationException` (params), and `ResponseEntityExceptionHandler` already answers them with a 400 `ProblemDetail`.

Override the base class's `handleMethodArgumentNotValid` to put one readable `detail` and a per-field `errors` property on the Problem:

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        final MethodArgumentNotValidException exception,
        final HttpHeaders headers,
        final HttpStatusCode status,
        final WebRequest request) {
    final Map<String, String> errorsByField = exception.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (first, second) -> first));
    final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    problemDetail.setProperty("errors", errorsByField);
    return ResponseEntity.badRequest().body(problemDetail);
}
```

Hand-written internal records don't cross the HTTP boundary, so they don't need validation.

## 6. Errors inside security filters

`@RestControllerAdvice` only sees exceptions from the DispatcherServlet down. Filters, the authentication entry point and the access-denied handler run before it, so they must **forward** errors to the handler:

```java
// in JwtAuthenticationFilter (OncePerRequestFilter), with an injected HandlerExceptionResolver
} catch (final JwtException exception) {
    log.debug("Rejected access token: {}", exception.getMessage());
    handlerExceptionResolver.resolveException(request, response, null,
            new UnauthorizedException("Invalid or expired access token"));
}
```

In `SecurityConfig`, the `AuthenticationEntryPoint` (unauthenticated → 401) and the `AccessDeniedHandler` (forbidden, including CSRF failures → 403) delegate to the same `HandlerExceptionResolver`. Every security failure then produces the same Problem body. Don't use `response.sendError(...)` or Spring's default entry points, since those produce a different body or a redirect.

## 7. Adding an exception type

Add one only when no existing class maps to the status you need (e.g. 422).

1. Create `<Name>Exception extends RuntimeException` with a single `(final String message)` constructor.
2. Add a handler to `GlobalExceptionHandler` that follows the shape in §3.
3. Keep specific handlers above the catch-all for readability. Spring picks the most specific type regardless of order.
4. Add the status to the affected operations' responses in `openapi.yaml`, with the `Problem` schema.

## 8. Do / Don't

**Do**
- Throw the most specific typed exception from the service layer, with a clear message.
- Let the global handler produce every error response as a `ProblemDetail`.
- Route filter, entry-point and access-denied errors through `HandlerExceptionResolver`.

**Don't**
- `try/catch` in controllers, or hand-build 4xx `ResponseEntity`s.
- Create a custom error body class alongside `ProblemDetail`.
- Use a bare `orElseThrow()`, or let `NoSuchElementException`/`IllegalArgumentException` escape (they become 500).
- Use `ConflictException` for authorization failures.
- Throw `ResponseStatusException` or a raw `RuntimeException`. Use the typed exceptions so every status is handled in one place.
- Put internal details in messages. Only the catch-all logs stack traces, and it never returns them.
