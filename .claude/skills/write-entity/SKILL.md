---
name: write-entity
description: Conventions for JPA entities in a Spring Boot / Hibernate / Flyway API — the Auditable base class (timestamps + optimistic-lock version), Lombok setup, ids, column and enum mapping, relationships (ManyToOne, OneToMany with cascade/orphanRemoval, ManyToMany join tables, element collections), ordered children, composite keys with @EmbeddedId/@MapsId, domain methods, and the matching Flyway migration. Use when adding an entity, adding or changing entity fields or relations, or writing the migration for a schema change.
---

# Writing JPA entities

Entities live in `{basePackage}.model.entity`. The schema is owned by **Flyway migrations**, not by Hibernate: every entity change ships with a new migration (§8), and `ddl-auto` must not create or alter tables.

Related skills: `write-repository`, `write-dto`, `write-mapper`.

## 1. Skeleton

```java
package {basePackage}.model.entity;

@Table(name = "product", schema = "public")
@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Product extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductCategory category;

    @Column(nullable = false)
    private boolean featured;
}
```

- Lombok: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`. JPA needs the no-args constructor, and `@Builder` needs the all-args one.
- **Don't** use `@Data`, `@EqualsAndHashCode` or `@ToString` on entities. They trigger lazy loading and recursion through bidirectional relations. The only exception is `@EqualsAndHashCode` on `@Embeddable` id classes (§6).
- Name the table explicitly as the singular snake_case noun (`product`, `playlist_track`). Use a plural name only for reserved words (`users`).
- Ids are `Long` with `GenerationType.IDENTITY`, matching a `BIGSERIAL`/identity column in the migration.
- Entities extend `Auditable` unless they're pure lookup or reference data or join rows without their own lifecycle.

## 2. The `Auditable` base class

Create it once, if it doesn't exist, and enable auditing with `@EnableJpaAuditing` on the application class:

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class Auditable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    protected LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    protected LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    protected Long version;
}
```

- `version` enables **optimistic locking**. Never set it by hand, and never expose it for clients to write.
- Every table of an `Auditable` entity needs `created_at`, `updated_at` and `version` columns (§8).
- Deletes are **hard deletes**. Don't add `is_deleted` flags or soft-delete filters.

## 3. Columns and types

- Put `nullable = false` on every required field, and make it match `NOT NULL` in the migration. Give strings a `length` that matches `VARCHAR(n)`. Put `unique = true` on naturally unique fields (username, email, name of a lookup), backed by a DB unique constraint.
- Java field names are camelCase and map to snake_case columns by default. Use `@Column(name = "...")` only when the column name differs (e.g. `@Column(name = "cover_url") private String coverKey;`). Prefer renaming the column in a migration over leaving mismatched names.
- Enums: **always** `@Enumerated(EnumType.STRING)`, never ordinal. Enums live in `{basePackage}.util.enums`.
- Use primitives (`int`, `boolean`) only for `NOT NULL` columns, and wrappers (`Integer`, `Long`) for nullable ones.
- Booleans: name the field without an `is` prefix (`featured`, `generated`). Lombok already generates `isFeatured()`, and an `is` prefix on the field causes property-name mismatches in MapStruct and Jackson.
- Timestamps: `LocalDateTime` (or `Instant` if the project standardizes on UTC instants). Store dates as date types, never as `String`.
- Range-validated values may carry `@Min`/`@Max` using shared `Constants`. Enforce them in the DB too with a `CHECK` constraint.

## 4. Relationships

**Many-to-one (owning side, the default for references)**
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "owner_id", nullable = false)
private User owner;
```
Always use `FetchType.LAZY` (the JPA default for `@ManyToOne` is EAGER). Put `nullable = false` on the join column when the relation is required.

**Self-reference (lineage, parent)**
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "copied_from")
private Playlist copiedFrom;          // FK with ON DELETE SET NULL in the migration
```

**One-to-many parent → children (aggregate)**
```java
@Builder.Default
@OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("orderIndex ASC")
private Set<PlaylistTrack> tracks = new LinkedHashSet<>();
```
- The child owns the FK (`@ManyToOne @JoinColumn(name = "playlist_id", nullable = false)`), and the parent uses `mappedBy`.
- Use `cascade = ALL, orphanRemoval = true` only when the parent truly owns the children's lifecycle.
- For ordered children, the child has an `int orderIndex` column, the parent collection uses `@OrderBy("orderIndex ASC")`, and the collection is a `LinkedHashSet`.

**Many-to-many (simple link, no extra columns)**
```java
@Builder.Default
@ManyToMany
@JoinTable(
        name = "product_category",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
)
private Set<Category> categories = new HashSet<>();
```
The inverse side (if needed) uses `@ManyToMany(mappedBy = "categories")`. Make the join table's PK `(product_id, category_id)`. If the link needs extra columns (priority, grantedAt, expiresAt), model it as its own entity (§6) instead.

**Element collections (lists of values)**
```java
@Builder.Default
@ElementCollection
@CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
@Column(name = "tag")
private List<String> tags = new ArrayList<>();
```

**Collection rules**
- Initialize every collection field, **and put `@Builder.Default` on it**. Without it, `Entity.builder().build()` leaves the collection `null` and the initializer is ignored.
- Use `Set` for relations and `List` for ordered value collections.
- Avoid `FetchType.EAGER` on collections. Load them on demand with fetch-join finders (see `write-repository`).

## 5. Domain methods

Put behaviour that protects an entity's own invariants **on the entity**, especially adding children so both sides of the relation stay in sync:

```java
public void assignRole(final Role role, @Nullable final LocalDateTime expiresAt) {
    final boolean alreadyAssigned = this.userRoles.stream()
            .anyMatch(userRole -> userRole.getRole().equals(role) && userRole.isActive());
    if (alreadyAssigned) {
        throw new ConflictException("Role already assigned");
    }
    final UserRole userRole = new UserRole();
    userRole.setUser(this);
    userRole.setRole(role);
    userRole.setGrantedAt(LocalDateTime.now());
    userRole.setExpiresAt(expiresAt);
    this.userRoles.add(userRole);
}
```

- Domain methods may throw the typed exceptions from `exception/` (see `exception-handling`).
- They must not call repositories, services or other Spring beans.
- Keep `final` on parameters and locals, as elsewhere.

## 6. Join entities with extra columns (composite keys)

```java
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserRoleId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role_id")
    private Long roleId;
}

@Entity
@Table(name = "user_roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

    @Builder.Default
    @EmbeddedId
    private UserRoleId id = new UserRoleId();   // must be non-null for @MapsId to populate it

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;              // null = never expires
}
```

The id class needs `Serializable`, `@EqualsAndHashCode` and a no-args constructor. Temporal grants use nullable `expiresAt`, and "active" means `expiresAt IS NULL OR expiresAt > now`.

## 7. Security-principal entity

If the user entity implements `UserDetails`, compute `getAuthorities()` from active role grants only, and keep the other `UserDetails` methods delegating to their defaults unless there's real account-state logic. Never expose the password field through DTOs.

## 8. The Flyway migration (required)

Every entity change needs a **new** migration in `src/main/resources/db/migration/`, named `V{next}__{snake_case_description}.sql`. Never edit a migration that has already been applied.

```sql
CREATE TABLE product (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    category    VARCHAR(255) NOT NULL,
    featured    BOOLEAN      NOT NULL DEFAULT FALSE,
    owner_id    BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_product_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_product_owner_id ON product(owner_id);
```

- Enum columns are `VARCHAR(255)`, matching `EnumType.STRING`.
- Choose each FK's `ON DELETE` on purpose. Use `CASCADE` for owned children, `SET NULL` for optional references such as lineage, and the default (restrict) for references that should block deletes.
- Index FK columns that are used in lookups (`owner_id`, `playlist_id`).
- Make column nullability, lengths and unique constraints match the entity annotations exactly.
- For renames and drops, use `ALTER TABLE ... RENAME COLUMN / DROP COLUMN` in a new migration, and update the entity in the same change.

## 9. Checklist

- [ ] Entity in `{basePackage}.model.entity`, with the Lombok set from §1 and no `@Data`
- [ ] Extends `Auditable` (unless lookup or join data), `Long` id with IDENTITY
- [ ] `nullable`/`length`/`unique` match the migration, and enums are `STRING`
- [ ] Relations are LAZY, with the FK on the owning side, `mappedBy` on the inverse side, and cascade only for owned children
- [ ] Every collection is initialized **with `@Builder.Default`**
- [ ] Booleans have no `is` prefix
- [ ] New `V{next}__*.sql` migration with the audit columns, FKs (with deliberate `ON DELETE`) and indexes
- [ ] Repository (`write-repository`), DTOs (`write-dto`) and mapper (`write-mapper`) updated
