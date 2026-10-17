---
paths:
  - "**/*.java"
---

# Java style rules

## `final` and immutability
- Mark **every** method parameter and local variable `final`. Drop `final` only on a local that is genuinely reassigned (loop counters, accumulators), and prefer restructuring the code first.
- Injected dependencies are `private final` fields set by Lombok's `@RequiredArgsConstructor` (or `@AllArgsConstructor`). Never use `@Autowired` on fields or setters.
- Use `record`s for immutable hand-written data carriers (internal DTOs, filter objects, config properties, value objects). API models generated from `openapi.yaml` are exempt: never edit or wrap them by hand.
- Return unmodifiable collections: `.toList()`, `List.of(...)`, `Set.copyOf(...)`. Never mutate a collection you received as a parameter unless that's the method's documented purpose.

```java
// good
public ProductResponseDto getProductById(final Long id) {
    final Product product = productRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found"));
    return productMapper.toProductResponseDto(product);
}
```

## Naming
- **Classes** are nouns with a role suffix: `*Controller`, `*Service` / `*ServiceImpl`, `*Repository`, `*Mapper`, `*Specifications`, `*RequestDto` / `*ResponseDto` / `*FilterDto`, `*Properties`, `*Exception`, `*Validator`, `*Filter`, `*Config`.
- **Methods** are verbs that say what happens: `get`, `find`, `create`, `update`, `delete`, `exists`, `build`, `resolve`, `validate`, `calculate`, `apply`. A method that loads *and* checks something says so (`getAndValidateProducts`).
- **Booleans**: methods and locals read as questions (`isTokenValid`, `hasActiveRole`, `containsDuplicates`). For fields on entities and records, see the entity and DTO skills (no `is` prefix on fields).
- **Variables** carry their meaning: `latestOrderId`, `missingIds`, `accumulatedDuration`. No abbreviations (`usr`, `cnt`, `tmp`) or single letters, except conventional lambda params in tiny lambdas (`p ->` is acceptable, but `product ->` is better) and Criteria API `root`, `query`, `cb`.
- Constants are `UPPER_SNAKE_CASE`, and enum constants too.
- Packages are lowercase or camelCase nouns per layer (`controller`, `service.implementations`, `model.dto.<domain>`, `util.enums`).
- Name collections in the plural (`products`, `categoryIds`) and maps as `valueByKey` (`scoreById`, `priorityByArea`).

## Methods and structure
- One method does one thing at one level of abstraction. Extract private helpers when a method needs comments to separate its steps.
- Use guard clauses and early returns/throws instead of nested `if`/`else`.
- Class order: constants → fields → constructors → public methods (interface order) → private helpers.
- `Optional` is **only** a return type. Never use it as a field, a parameter or in a collection. Don't call `.get()` without a check; use `orElseThrow(...)`, `map`, `orElse`.
- Never return `null` for a collection; return an empty one.
- Use streams for mapping, filtering and collecting. Use a plain loop when you need an index, early exit or mutable state.
- Prefer `switch` expressions and pattern matching (Java 21) over long `if`/`else if` chains.

## Lombok
- Entities: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`, with `@Builder.Default` on initialized fields. **No `@Data`, `@EqualsAndHashCode` or `@ToString`** on entities.
- Spring beans: `@RequiredArgsConstructor` (+ `@Slf4j` if they log).
- Holders of static members only: `@UtilityClass`.
- Enums with attributes: `@Getter @RequiredArgsConstructor`.

## Imports
- No unused imports, and no wildcard imports in new code.
- Use static imports for frequently used constants (`import static ...Constants.DEFAULT_PAGE;`).

## Comments
- Comments explain **why**, not what. Well-named methods replace "what" comments.
- A `// TODO:` must state what's missing or wrong (`// TODO: restrict to ADMIN once roles are assigned on registration`).
- Don't commit commented-out code (git keeps the history).

## Logging
- Use `@Slf4j` and `log.<level>` with `{}` placeholders. Never use string concatenation, `System.out` or `printStackTrace()`.
- Levels: `debug` for diagnostics, `info` for significant lifecycle events, `warn` for unexpected but handled situations, and `error` for failures that need attention (pass the exception as the last argument).
- Never log passwords, tokens, secrets or full request bodies.
