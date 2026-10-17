---
name: write-repository
description: Conventions for Spring Data JPA repositories in a Spring Boot API — interface layout, JpaSpecificationExecutor for filtered lists, derived query naming, owner-scoped finders, existence checks, @Query JPQL with text blocks and JOIN FETCH, parameter binding, and composite-key repositories. Use when adding a repository, adding a finder or query method, or when a service needs a new data-access method.
---

# Writing repositories

Repositories are the only layer that talks to the database. Services call them, and controllers never do. They live in `{basePackage}.repository`, one per aggregate root or entity that needs direct access.

Related skills: `write-entity` (what's being queried), `write-service` (the caller) and the service skill's Specifications section (dynamic filtering).

## 1. Skeleton

```java
package {basePackage}.repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndOwnerId(final Long id, final Long ownerId);

    boolean existsBySku(final String sku);
}
```

- Always an **interface** extending `JpaRepository<Entity, IdType>`. Put `@Repository` on every repository for consistency.
- Add `JpaSpecificationExecutor<Entity>` whenever the entity has a filtered or paginated list endpoint. Dynamic filters go through Specifications, never through combinations of derived methods or string-built queries.
- Every parameter is `final`.
- Don't write a repository for a child entity that's only ever managed through its parent's cascade, unless a service needs to query it directly.

## 2. Derived query methods (preferred)

Use Spring Data method-name queries whenever the query is a simple conjunction:

| Need | Method |
|---|---|
| Single optional result | `Optional<X> findByUsername(final String username)` |
| Owner-scoped lookup | `Optional<X> findByIdAndOwnerId(final Long id, final Long ownerId)` |
| List by field(s) | `List<X> findAllByCategory(final Category category)`, `List<X> findAllByUserIdAndBatchId(...)` |
| Null checks | `List<X> findAllByBatchIdAndParentIsNull(final Long batchId)` |
| Existence / uniqueness | `boolean existsByEmail(final String email)`, `boolean existsByProductId(final Long productId)` |
| Count | `long countByOwnerId(final Long ownerId)` |

Rules:
- **Single results return `Optional<X>`**, so the service can use `.orElseThrow(...)`. Never return a nullable entity (`X findByName(...)`).
- Use `existsBy...` for uniqueness checks and "is it referenced?" delete guards. Don't load an entity just to check that it exists.
- Traverse relations with the property path: `ownerId` resolves to `owner.id`, and `productId` resolves to `product.id`.
- Owner-scoped finders (`...AndOwnerId`) are how ownership is enforced. Add one for every user-owned entity that is fetched, updated or deleted by id.

## 3. `@Query` (JPQL) when derived names don't fit

Use `@Query` for aggregates (`MAX`, `COUNT`), conditions that span several joins, fetch joins, or time-based conditions. Write JPQL (not native SQL) in a text block, with meaningful aliases:

```java
@Query("""
        SELECT MAX(record.batchId)
        FROM AssessmentRecord record
        WHERE record.user.id = :userId
        """)
Optional<Long> findMaxBatchIdByUserId(@Param("userId") final Long userId);

@Query("""
        SELECT track FROM PlaylistTrack track
        WHERE track.id = :trackId
          AND track.playlist.id = :playlistId
          AND track.playlist.owner.id = :ownerId
        """)
Optional<PlaylistTrack> findByIdAndPlaylistIdAndOwnerId(
        @Param("trackId") final Long trackId,
        @Param("playlistId") final Long playlistId,
        @Param("ownerId") final Long ownerId);
```

- Bind every named parameter with `@Param("name")`, and make the Java parameter name match the placeholder. Mismatched names (e.g. `:playlistId` bound to a parameter called `playlist_id`) fail at startup or at runtime.
- Use camelCase for aliases and parameters, and JPQL entity and field names (not table or column names).
- Boolean existence with extra conditions:
  ```java
  @Query("""
          SELECT CASE WHEN COUNT(userRole) > 0 THEN true ELSE false END
          FROM UserRole userRole
          WHERE userRole.user.id = :userId
            AND userRole.role.name = :roleName
            AND (userRole.expiresAt IS NULL OR userRole.expiresAt > CURRENT_TIMESTAMP)
          """)
  boolean hasActiveRole(@Param("userId") final Long userId, @Param("roleName") final String roleName);
  ```
- Temporal validity (grants, tokens, subscriptions) is written in the query (`expiresAt IS NULL OR expiresAt > CURRENT_TIMESTAMP`), not filtered in Java after loading.
- Only use native queries (`nativeQuery = true`) for database-specific features that JPQL can't express.

## 4. Fetching associations (avoid N+1 and lazy errors)

- When a caller always needs a lazy association, add a dedicated finder with `JOIN FETCH` and name it after what it loads:
  ```java
  @Query("""
          SELECT user FROM User user
          LEFT JOIN FETCH user.userRoles userRole
          LEFT JOIN FETCH userRole.role
          WHERE user.username = :username
          """)
  Optional<User> findByUsernameWithRoles(@Param("username") final String username);
  ```
- Use `LEFT JOIN FETCH` when the association may be empty, and `JOIN FETCH` when it's mandatory.
- Alternatively, annotate a derived method with `@EntityGraph(attributePaths = {"owner", "tracks"})`.
- Don't fetch-join more than one collection in the same query (it creates a Cartesian product), and never combine a collection fetch join with pagination.

## 5. Composite keys

For join entities with an `@EmbeddedId`, the repository's id type is the embeddable class:

```java
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> { ... }
```

## 6. Writes

- The built-in `save`, `saveAll`, `delete`, `deleteById` and `findAllById` cover almost everything. Call them from `@Transactional` service methods.
- `findAllById(ids)` silently skips unknown ids. The service must compare sizes and report the missing ids.
- For bulk updates or deletes, use `@Modifying` + `@Query` (with `clearAutomatically = true` when the persistence context may hold stale entities), and call it inside a transaction.
- Deletes are hard deletes, and cascades come from the entity mappings and database foreign keys. Check what blocks a delete (`existsBy...`) in the service before deleting.

## 7. Checklist

- [ ] Interface in `{basePackage}.repository` with `@Repository`, extending `JpaRepository<X, IdType>`
- [ ] `JpaSpecificationExecutor<X>` if the entity has filtered or paginated lists
- [ ] Single-result finders return `Optional`
- [ ] Owner-scoped `findByIdAndOwnerId` for user-owned entities, and `existsBy...` for uniqueness and delete guards
- [ ] `@Query` uses JPQL text blocks, with `@Param` names matching the placeholders
- [ ] Fetch joins or entity graphs only where a caller needs the association
- [ ] No leftover IDE-generated stray methods (e.g. `Long user(User user)`) that don't map to a real query

## 8. Anti-patterns

- Returning a nullable entity instead of `Optional`.
- Loading entities just to test existence or count them.
- String-concatenated or dynamically built JPQL (use Specifications instead).
- Fetching everything eagerly on the entity to avoid writing a fetch query.
- Business rules in default methods on the repository interface.
