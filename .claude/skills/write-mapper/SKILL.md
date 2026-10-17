---
name: write-mapper
description: Conventions for MapStruct mappers in an OpenAPI-first Spring Boot / JPA API — entity↔generated-DTO conversion, request-to-entity creation, partial updates with @MappingTarget, building internal filter records from query params, page responses, enum and date-time conversions, multi-source methods, nested mappers via `uses`, custom @Named conversions, and which target fields to ignore. Use when adding or changing a mapper, adding a DTO or entity field that must be mapped, or when a service or controller needs a new conversion.
---

# Writing MapStruct mappers

Mappers are the **only** place where entities and DTOs are converted. Controllers and services call mapper methods and never copy fields by hand. Mappers live in `{basePackage}.mapper`, one per domain (`UserMapper`, `EntitlementMapper`, `ProgramMapper`, `ProgramDayMapper`, `ProgramBlockMapper`), plus shared ones such as `DateTimeMapper`.

The DTO side is either a **generated API model** (`{basePackage}.api.model.*Dto`, from `openapi.yaml`) or a hand-written **internal record** (`{basePackage}.model.dto.<domain>`). See `write-dto`.

Related skills: `write-service` (the main caller), `write-controller` (builds request-side records through mappers) and `write-dto` (the targets).

## 1. Mapper skeleton

```java
package {basePackage}.mapper;

@Mapper(config = CentralMapperConfig.class, uses = DateTimeMapper.class)
public interface EntitlementMapper {

    EntitlementResponseDto toEntitlementResponseDto(final Entitlement entitlement);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "revokedAt", ignore = true)
    Entitlement toEntitlement(final EntitlementRequestDto entitlementRequestDto,
                              final EntitlementSource source, final User user);
}
```

- Always write an **interface** with `@Mapper(config = CentralMapperConfig.class)`, so it's a Spring bean injected with `private final` in services and controllers. Never call `Mappers.getMapper(...)`.
- Every parameter is `final`.
- Keep mappers free of side effects. No repositories, no DB access, no business rules.

## 2. Method naming

| Purpose | Signature |
|---|---|
| Entity → response DTO | `XResponseDto toXResponseDto(final X x)` |
| List of entities → DTOs | `List<XResponseDto> toXResponseListDto(final List<X> xs)` |
| Request DTO → new entity | `X toX(final XRequestDto dto)` |
| Request DTO + context → new entity | `X toX(final XRequestDto dto, final Visibility visibility, final User owner)` |
| Apply update to an existing entity | `void updateXFromDto(final XRequestDto dto, @MappingTarget final X x)` |
| Query params → filter DTO | `XFilterDto toXFilterDto(final String name, final Category category, ...)` |
| Page of entities → page response | `XPageResponseDto toXPageResponseDto(final Page<X> page)` (map `content`, `number` → `page`, `size`, `totalElements`, `totalPages`) |
| Internal record → response DTO | `ImportReportResponseDto toImportReportResponseDto(final ImportReport importReport)` |

When the mapper already declares the element method, MapStruct generates list and set methods from it automatically. Declare them only when a caller needs them.

## 3. Direction-specific rules

### Entity → response DTO
- Usually needs no annotations: same-named fields map automatically, including nested DTOs (via `uses`, §5) and enums.
- **Enums:** the spec's enum values match the domain enums in `util.enums` by name (both UPPER_CASE), so MapStruct maps them automatically. If a name differs, map it explicitly with `@ValueMapping(source = "...", target = "...")`. Never convert through `name()` strings by hand.
- **Date-times:** entities use `LocalDateTime`, and generated models use `OffsetDateTime` for `format: date-time`. Convert them in the shared `DateTimeMapper` (a `@Named` method that uses UTC), added through `uses`. `LocalDate` maps as it is.
- To flatten a nested field, use a dotted `source`: `@Mapping(target = "playlistId", source = "track.playlist.id")`.
- Never expose sensitive fields (password hashes, tokens). Leave them out of the response DTO rather than ignoring them in the mapper.

### Request DTO → new entity (`toX`)
- Ignore every field that the **server** controls or the service sets:
  - `@Mapping(target = "id", ignore = true)`
  - child collections that the service builds (`@Mapping(target = "tracks", ignore = true)`)
  - relations resolved from ids by the service (`categories`, `owner`, when they aren't passed as parameters)
  - audit and locking fields (`createdAt`, `updatedAt`, `version`), if MapStruct can see them
- Pass values the service decides (owner, visibility, generated flags) as **extra parameters** instead of setting them afterwards:
  ```java
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tracks", ignore = true)
  Playlist toPlaylist(final PlaylistRequestDto dto, final PlaylistVisibility visibility,
                      final boolean generated, final User owner);
  ```
  A parameter whose name matches a target property (`visibility`, `owner`) maps with no `@Mapping`.
- Multi-source methods can also build child entities from plain values:
  ```java
  @Mapping(target = "id", ignore = true)
  PlaylistTrack toPlaylistTrack(final int duration, final Playlist playlist, final Track track, final int orderIndex);
  ```

### Partial update (`updateXFromDto`)
- Signature: `void updateXFromDto(final Dto dto, @MappingTarget final X entity)`. It changes the managed entity in place, and the service then saves it.
- **Explicitly ignore everything the update must not touch**: `id`, `owner`, child collections, server-computed values (durations, totals), flags like `generated`/`visibility` when they aren't editable, lineage (`copiedFrom`) and audit/version fields.
- Use a dedicated update DTO (`UpdateUserPlaylistRequestDto`) when the editable fields differ from the create fields. That's clearer than ignoring half of the create DTO.
- For PATCH-like semantics, where null means "leave unchanged", add `@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)`. Without it, nulls in the DTO overwrite entity values.

### Query params → filter or pagination DTO
Controllers use these to turn loose query params from the generated signature into one internal `XFilterDto` record. Parameter names must match the record's component names.

## 4. Naming mismatches: booleans and `is` prefixes

Lombok generates `isDoubleSided()` for a `boolean doubleSided` entity field, and MapStruct sees the property as `doubleSided`. A spec property or record component named `isDoubleSided` exposes the property `isDoubleSided`. Map them explicitly in **both** directions:

```java
@Mapping(target = "isFeatured", source = "featured")          // entity -> DTO
ProductResponseDto toProductResponseDto(final Product product);

@Mapping(target = "featured", source = "isFeatured")          // DTO -> entity
void updateProductFromDto(final ProductRequestDto dto, @MappingTarget final Product product);
```

The same applies to `toX(...)`. If you forget it, the field silently stays at its default (`false`). The simpler fix is to name the spec property or record component without `is` (`featured`).

Only write `@Mapping(target = "x", source = "x")` when the names differ or the source is ambiguous. Same-name mappings are redundant noise.

## 5. Composing mappers with `uses`

```java
@Mapper(config = CentralMapperConfig.class, uses = { UserMapper.class, PlaylistTrackMapper.class })
public interface PlaylistMapper { ... }
```

- Add a mapper to `uses` when the target contains nested DTOs that another mapper already knows how to build (`owner` → `UserResponseDto`, `tracks` → `List<PlaylistTrackResponseDto>`).
- Avoid **cycles** (`A uses B` and `B uses A`). They make the dependency graph fragile and can recurse through bidirectional JPA relations. If a child DTO only needs the parent's id, flatten it with `source = "child.parent.id"` instead of `uses`-ing the parent mapper.
- Response DTOs for bidirectional relations must not embed each other both ways (e.g. playlist → tracks → playlist), or mapping recurses infinitely.

## 6. Custom conversions (`@Named` default methods)

For small, pure value conversions, use a `default` method with `@Named`, and reference it with `qualifiedByName`:

```java
@Mapping(target = "tags", source = "tagNames", qualifiedByName = "normalizeTags")
Product toProduct(final ProductRequestDto dto);

@Named("normalizeTags")
default Set<String> normalizeTags(final Set<String> tagNames) {
    if (tagNames == null) {
        return Collections.emptySet();
    }
    return tagNames.stream().map(String::trim).map(String::toLowerCase).collect(Collectors.toSet());
}
```

- Handle `null` input explicitly.
- **Id → entity relations:** the preferred approach is to `ignore` the relation in the mapper and let the service load the entities (`repository.findAllById(ids)`), check that every id was found, and set them. If a mapper does turn ids into id-only stub entities, be aware that nothing checks the ids exist, and the stubs have no other fields loaded.
- **Conversions that need a Spring bean** (e.g. password hashing with `PasswordEncoder`) don't belong in an interface `default` method that calls `new` on the bean class. Either do them in the service, or turn the mapper into an `abstract class` with the bean `@Autowired` and a `@Named` method that uses it.

## 7. Build setup and safety net

- Declare `mapstruct` as a dependency and `mapstruct-processor` in `maven-compiler-plugin` → `annotationProcessorPaths`, **after** `lombok`. Add `org.projectlombok:lombok-mapstruct-binding` too, so MapStruct sees the Lombok-generated getters, setters and builders.
- Every mapper uses the shared config, which turns unmapped target properties into **compile errors**. A new spec property or entity field that nobody maps then fails the build instead of silently staying `null`:
  ```java
  @MapperConfig(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
  public interface CentralMapperConfig { }

  @Mapper(config = CentralMapperConfig.class, uses = { ... })
  ```
- Generate models with `generatedConstructorWithRequiredArgs=false`, so MapStruct builds them through the no-args constructor and setters. Otherwise it can pick a partial constructor.
- The generator runs in `generate-sources`, before annotation processing, so mappers always see the current models. After a spec change, `./mvnw compile` shows which mappers need updating.
- After changing a mapper, build (`mvn clean compile`) and check the generated `target/generated-sources/annotations/.../<X>MapperImpl.java` whenever mapping behaviour is unclear.

## 8. Checklist

- [ ] Interface, `@Mapper(config = CentralMapperConfig.class)`, in `{basePackage}.mapper`
- [ ] Targets are generated `api.model.*Dto` classes or internal records, never hand-written copies of API models
- [ ] Enums map by name (or `@ValueMapping`), and `LocalDateTime` ↔ `OffsetDateTime` goes through `DateTimeMapper`
- [ ] Method names follow §2, with all params `final`
- [ ] `toX` and `updateXFromDto` ignore `id`, server-controlled fields, service-built relations and audit/version fields
- [ ] Context values (owner, visibility, flags) passed as extra parameters, not set by hand afterwards
- [ ] `is`-prefixed booleans mapped explicitly in both directions
- [ ] Nested DTOs through `uses`, with no mapper cycles and no bidirectional DTO embedding
- [ ] No repositories, DB access or business logic in the mapper
- [ ] Build passes with no new unmapped-target warnings

## 9. Anti-patterns

- Copying fields by hand in services or controllers instead of adding a mapper method.
- Hand-writing a list mapping with a stream when a mapper list method could be declared.
- `toX`/`updateXFromDto` without `ignore` on `id`, which can overwrite the primary key from client input.
- Update mappers that let a client change ownership, lineage or server-computed fields.
- Redundant same-name `@Mapping`s, or copy-pasted ones that don't match the method's actual parameters.
- Instantiating Spring beans (`new BCryptPasswordEncoder()`) or querying repositories inside a mapper.
- Setting fields on a generated DTO after the mapper returned it, instead of mapping them in the mapper.
