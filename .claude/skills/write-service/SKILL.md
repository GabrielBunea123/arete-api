---
name: write-service
description: Conventions for the service layer of a Spring Boot / JPA API — interface + Impl split, constructor injection, transactions, resolving the authenticated user and enforcing ownership, pagination with JPA Specifications, MapStruct mapping, building parent/child aggregates, and cross-service dependencies. Use when adding a service or service method, changing business logic, or reviewing service code.
---

# Writing services

The service layer is where business logic lives. Controllers call services, and services call repositories, MapStruct mappers, JPA Specifications and other services. `{basePackage}` means the project's root package. Reuse existing helpers (`PaginationUtils`, specifications, mappers, `UserService`) before writing new ones.

Related skills: `write-controller` (the caller) and `exception-handling` (what to throw).

## 1. Structure: interface + Impl

```
{basePackage}/service/
  ProductService.java                ← interface (the contract controllers depend on)
  implementations/
    ProductServiceImpl.java          ← @Service implementation
```

```java
package {basePackage}.service;

public interface ProductService {
    ProductPageResponseDto getProducts(
            final int page,
            final int size,
            final ProductFilterDto productFilterDto);
    ProductResponseDto getProductById(final Long id);
    ProductResponseDto createProduct(final ProductRequestDto productRequestDto);
    ProductResponseDto updateProduct(final Long id, final ProductRequestDto productRequestDto);
    void deleteProduct(final Long id);
}
```

```java
package {basePackage}.service.implementations;

@Slf4j                      // only if the class logs
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductSpecifications productSpecifications;
    private final ProductMapper productMapper;
    private final UserService userService;            // other services: by INTERFACE

    @Override
    public ProductResponseDto getProductById(final Long id) { ... }
}
```

Rules:
- Every service has an interface in `service/` and an `<Name>ServiceImpl` in `service/implementations/`. Framework adapters (e.g. a `UserDetailsService` implementation) are the exception and may be a single class.
- Inject with `@RequiredArgsConstructor` and `private final` fields. Don't use `@Autowired`, and don't use package-private fields.
- Mark every implemented method `@Override`. Every parameter is `final`, in both interface and impl, and locals are `final` unless they're reassigned.
- Name methods after the action and resource: `getXById`, `getXs`, `createX`, `updateX`, `deleteX`. Use `...UserX` (`getUserPlaylists`) when the method works on the authenticated user's own data.
- Put private helpers at the bottom of the class, with verb names (`buildPlaylistTracks`, `getAndValidateProducts`).
- Tuning numbers for a service's algorithm go in `private static final` constants at the top of the class. Values shared across the app go in `util/Constants`.

## 2. Inputs and outputs

- Public methods **take generated request DTOs, internal records or ids**, and **return generated response DTOs** (`XResponseDto`, `XPageResponseDto`), `List<XResponseDto>` or `void`. Never return entities to controllers. Never take the caller's user id or username as a parameter, since the service resolves the caller itself (§3).
- The one allowed exception is a service-to-service helper such as `UserService.getAuthenticatedUser()`, which returns the `User` **entity** so other services can use it as an owner or foreign key. Controllers never call it.
- Services called outside a request (importer runners, `AdminBootstrap`) may take a `User` or user id explicitly, because there is no authenticated caller.
- All entity↔DTO conversion goes through **MapStruct** (`productMapper.toProductResponseDto(entity)`, `productMapper.updateProductFromDto(dto, @MappingTarget entity)`). Mapper methods can take extra context arguments (`toPlaylist(dto, visibility, owner)`). Add such a method before you build the object by hand.
- An entity's Lombok `@Builder` is fine when it's created from other entities rather than from a request DTO (copies, generated data).

## 3. Resolving the user and checking ownership

Generated controller signatures can't carry the principal, so the **service** resolves the caller:

```java
final User user = userService.getAuthenticatedUser();   // SecurityContext → users by email; UnauthorizedException if missing
```

`UserServiceImpl.getAuthenticatedUser()` reads the authentication name (the email in the JWT subject) from `SecurityContextHolder` and loads the user. It's the only place that touches the security context. Other services never read `SecurityContextHolder` themselves.

Enforce ownership **in the query**, not afterwards in Java:

```java
final Playlist playlist = playlistRepository.findByIdAndOwnerId(playlistId, user.getId())
        .orElseThrow(() -> new NotFoundException("Playlist not found"));

final PlaylistTrack track = playlistTrackRepository
        .findByIdAndPlaylistIdAndOwnerId(trackId, playlistId, user.getId())
        .orElseThrow(() -> new NotFoundException("Playlist track not found"));
```

- Another user's resource gets the same 404 as a missing one (see `exception-handling`).
- Don't load with `findById` and then compare `entity.getOwner() != user`. That compares references, and the query already does the job.
- For lists, put the owner into the Specification: `specifications.buildFilters(user, filterDto)`.
- **Every** method that takes an id of user-owned data must scope by the caller, including reads. Otherwise anyone can read anyone's data by guessing ids.

## 4. Pagination and filtering

```java
@Override
public ProductPageResponseDto getProducts(
        final int page,
        final int size,
        final ProductFilterDto productFilterDto) {
    final Pageable pageable = PageRequest.of(page, size);
    final Specification<Product> spec = productSpecifications.buildFilters(productFilterDto);
    final Page<Product> productsPage = productRepository.findAll(spec, pageable);
    return productMapper.toProductPageResponseDto(productsPage);
}
```

- Page, size and sort come from the spec's query params (with defaults and bounds there). Build the `Pageable` with `PageRequest.of(...)`, and if the same sort handling is needed in several services, extract a `PaginationUtils` `@UtilityClass` in `util/`.
- The mapper turns the `Page<X>` into the resource's `XPageResponseDto` (see `write-mapper`).
- Filtering goes in a `@Component` `<X>Specifications` class in `specification/`, with one static predicate per filter that returns `null` when that filter is empty. `buildFilters(...)` combines them with `Specification.allOf(...)`.
- The repository extends `JpaRepository<X, Long>, JpaSpecificationExecutor<X>`. Don't write JPQL for dynamic filters.
- Unpaginated lists use `repository.findAll(...)`, then `stream().map(mapper::toX).toList()` or a mapper list method.

## 5. Common method recipes

**Get by id**
```java
return productRepository.findById(id)
        .map(productMapper::toProductResponseDto)
        .orElseThrow(() -> new NotFoundException("Product not found"));
```

**Create**: map the DTO, resolve related entities by id, **check that every id was found**, then save and map back:
```java
final Product product = productMapper.toProduct(dto);
final Set<Category> categories = new HashSet<>(categoryRepository.findAllById(dto.categoryIds()));
if (categories.size() != dto.categoryIds().size()) {
    throw new NotFoundException("Some categories not found");
}
product.setCategories(categories);
return productMapper.toProductResponseDto(productRepository.save(product));
```
Validate **every** id collection in the request, since `findAllById` silently skips unknown ids. To report exactly what's missing, compute `missingIds` (requested ids minus found ids) and include them in the message.

**Update**: load (scoped to the owner if the data is user-owned), apply the changes with the mapper's `@MappingTarget` method, save and map:
```java
final Product product = productRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Product not found"));
productMapper.updateProductFromDto(dto, product);
return productMapper.toProductResponseDto(productRepository.save(product));
```

**Delete**: load or check existence, check what blocks the delete, then hard delete:
```java
final Product product = productRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Product not found"));
if (orderItemRepository.existsByProductId(id)) {
    throw new ConflictException("Cannot delete product: it is still referenced by one or more orders");
}
productRepository.delete(product);
```

**Uniqueness**: check with `existsBy...` **before** doing other work, and throw `ConflictException`.

## 6. Transactions

- Put `@Transactional` on every public method that **writes more than once** (a parent plus its children, several `save`/`saveAll` calls), on updates to loaded entities, and on check-then-write methods.
- Use `org.springframework.transaction.annotation.Transactional` (it supports `readOnly`, `propagation` and `rollbackFor`), and don't mix it with `jakarta.transaction.Transactional` in one codebase.
- Use `@Transactional(readOnly = true)` on reads that walk lazy associations. Don't rely on open-in-view outside web requests.
- Annotate the **public entry point**. Calling a `@Transactional` method on `this` bypasses the proxy, and a private helper's annotation never applies. Two public methods that share a private helper each need their own `@Transactional`.
- Entities carry an optimistic-lock `version`. Let `ObjectOptimisticLockingFailureException` propagate rather than swallowing it.

## 7. Parent/child aggregates (ordered children)

For a parent that owns an **ordered** collection (e.g. `Playlist` → `PlaylistTrack` with `orderIndex`):
- Use `LinkedHashSet` to keep insertion order, and set `orderIndex` from the list position (0-based).
- Save the parent first, then `saveAll` the children, then `parent.setChildren(new LinkedHashSet<>(savedChildren))` so the returned DTO includes them. Do all of it in one `@Transactional` method.
- When appending a single child, use `orderIndex = parent.getChildren().size()`.
- When copying an aggregate, build **new** child entities (never reuse the originals), set the new owner, and record the source (`copiedFrom`) if the model tracks it.
- Put this logic in private helpers (`createX`, `buildXChildren`, `getAndValidateY`) and reuse them across create, copy and generate paths.

## 8. Dependencies between services

- Depend on another service's **interface** when you need its business rule (`userService.getAuthenticatedUser`). Depend on its **repository** directly for plain data access.
- Avoid cycles. `UserService` should be a leaf that other domain services depend on, never the other way round.
- Large algorithms (recommendation, generation, scoring) get their own service, with the steps split into small private methods: resolve inputs → build candidates → score → select → build and save.

## 9. Exceptions and logging

- Throw the typed exceptions from `exception/` (see the `exception-handling` skill), and always give `orElseThrow` a message supplier.
- Add `@Slf4j` only to classes that log. Use `log.warn` for unusual but handled cases, and `log.error` only for failures that need investigating. Don't log routine 404s.
- Use placeholders (`log.warn("No session found for user {} on {}", user.getId(), date)`), not concatenation. Never log passwords, tokens or other secrets.

## 10. Checklist

- [ ] Interface method in `service/` with `final` params and DTO/record/id arguments (no caller id or username)
- [ ] Impl in `service/implementations/` with `@Service @RequiredArgsConstructor` and `private final` deps
- [ ] Caller resolved through `userService.getAuthenticatedUser()`, and user-owned data scoped by owner id in the query or specification
- [ ] Every referenced id validated (404), and uniqueness checked (409)
- [ ] Mapping done only through MapStruct
- [ ] `@Transactional` on multi-write, update and check-then-write methods
- [ ] Repository finders (`findByIdAndOwnerId`, `existsBy...`) and `JpaSpecificationExecutor` added as needed
- [ ] Controller wired up (`write-controller`), and the build passes
