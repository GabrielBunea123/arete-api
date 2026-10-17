---
name: write-migration
description: Conventions for Flyway database migrations in a Spring Boot / PostgreSQL API — versioned file naming and numbering, why applied migrations are immutable and how to fix a bad one with a new migration, templates for creating/altering/renaming/dropping tables and columns, backfilling data before adding constraints, seed and reference data, FKs and indexes, and keeping entities in sync. Use when changing the database schema, adding/altering/dropping a table or column, seeding data, or when Flyway fails to validate or migrate on startup.
---

# Writing Flyway migrations

The database schema is owned by **Flyway**, not by Hibernate. Every schema or reference-data change is a new SQL file in `src/main/resources/db/migration/`. Hibernate must never create or alter tables (`spring.jpa.hibernate.ddl-auto` is `none` or `validate`).

Related skills: `write-entity` (the Java side of every schema change), `write-dto`, `write-mapper`.

## 1. How Flyway works

- On startup, Flyway scans `db/migration/`, compares it with the `flyway_schema_history` table and runs every pending migration **in version order**, each in its own transaction (PostgreSQL supports transactional DDL).
- For each applied migration, Flyway stores its version, description and a **checksum** of the file contents.
- On every startup it **validates** the files against the history. If an applied file was changed (checksum mismatch), renamed or deleted, the app **fails to start**.
- Write SQL for the target database. The examples here are PostgreSQL, which also needs the `flyway-database-postgresql` dependency next to `flyway-core`.

## 2. Naming and numbering

```
V{N}__{snake_case_description}.sql
```

- Use a capital `V`, an integer version, **two** underscores and a snake_case description of *what changes*: `V7__add_description_to_product.sql`, `V8__create_order_item.sql`.
- To find the next `N`, sort the folder **numerically**. Alphabetical order puts `V10` before `V2`:
  ```bash
  ls src/main/resources/db/migration | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | tail -1
  ```
- Keep **one logical change per file**, such as creating one feature's tables or one cleanup. Don't bundle unrelated changes, and don't split one change across several files without a reason.
- Never reuse or go back to a lower version number. If two branches add the same version, the branch merged second renames its file **before merging**, while it's still unapplied everywhere.
- If the project documents its current highest migration (for example in `CLAUDE.md` or a README), update it.

## 3. The golden rule: applied migrations are immutable

**Once a migration has run against any shared database (another developer's machine, CI, staging or production), never edit, rename or delete it.** Always write a new migration.

Why:
- Editing the file changes its checksum, so `validate` fails and the app won't boot on every environment that already ran it.
- Even if you bypass validation, databases that ran the old version never see your edit. Environments drift silently, and a fresh database ends up different from production.

How to fix a mistake:

| Mistake in an applied migration | Fix in a new `V{next}` migration |
|---|---|
| Wrong column name | `ALTER TABLE t RENAME COLUMN bad TO good;` |
| Column that shouldn't exist | `ALTER TABLE t DROP COLUMN c;` |
| Missing `NOT NULL` / default / index | Backfill, then `ALTER ... SET NOT NULL` or `CREATE INDEX` (§5) |
| Wrong type or length | `ALTER TABLE t ALTER COLUMN c TYPE VARCHAR(200);` |
| Wrong seed row | `UPDATE` / `DELETE` it by natural key |

The **only** time you may edit a migration is when it has **never left your machine**: it isn't pushed and only your local database ran it. Even then, reset your local database (or delete that row from your local `flyway_schema_history` and undo its changes) so the edited file runs fresh.

Never run `flyway repair`, delete rows from `flyway_schema_history` or turn off `validate-on-migrate` on a shared database to make a checksum error go away. That hides drift instead of fixing it.

## 4. SQL templates

**Create a table (for an `Auditable` entity)**
```sql
CREATE TABLE order_item (
    id          BIGSERIAL    PRIMARY KEY,
    order_id    BIGINT       NOT NULL,
    product_id  BIGINT,
    note        VARCHAR(500),
    status      VARCHAR(255) NOT NULL,
    quantity    INTEGER      NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_order_item_order   FOREIGN KEY (order_id)   REFERENCES orders(id)  ON DELETE CASCADE,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE SET NULL,
    CONSTRAINT chk_order_item_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_order_item_order_id   ON order_item(order_id);
CREATE INDEX idx_order_item_product_id ON order_item(product_id);
```
- Include the audit columns `created_at`, `updated_at` and `version` on every table whose entity extends `Auditable`.
- Ids are `BIGSERIAL` to match `GenerationType.IDENTITY`.
- Enum columns are `VARCHAR(255)` to match `EnumType.STRING`.
- Name constraints explicitly: `fk_{table}_{ref}`, `uq_{table}_{cols}`, `chk_{table}_{rule}`, `idx_{table}_{cols}`. Named constraints can be dropped or altered later without looking up generated names.
- **Index every FK column** you look rows up by. PostgreSQL doesn't create these indexes automatically.
- Choose `ON DELETE` on purpose: `CASCADE` for owned children, `SET NULL` for optional references, and the default (restrict) when the reference should block the delete.

**Add a column**
```sql
ALTER TABLE product ADD COLUMN description VARCHAR(500);
```

**Add a required column to a table that has rows**
```sql
ALTER TABLE product ADD COLUMN tier VARCHAR(255) NOT NULL DEFAULT 'STANDARD';
```
Or backfill it, then add the constraint (§5).

**Rename**
```sql
ALTER TABLE product RENAME COLUMN user_id TO owner_id;
ALTER TABLE old_name RENAME TO new_name;
```

**Drop**
```sql
ALTER TABLE product DROP COLUMN legacy_flag;
DROP TABLE IF EXISTS obsolete_table;
```

**Change a type**
```sql
ALTER TABLE product ALTER COLUMN name TYPE VARCHAR(200);
-- Non-trivial conversions need an explicit USING clause:
ALTER TABLE product ALTER COLUMN weight TYPE INTEGER USING weight::INTEGER;
```

**Unique and check constraints**
```sql
ALTER TABLE product ADD CONSTRAINT uq_product_sku UNIQUE (sku);
ALTER TABLE review ADD CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5);
```

## 5. Changing tables that already hold data

Assume every table has production rows. The migration must succeed on real data, not only on an empty dev database.

- **To add `NOT NULL` to an existing column**, backfill first, then add the constraint, all in the same migration:
  ```sql
  UPDATE product SET stock = 0 WHERE stock IS NULL;
  ALTER TABLE product
      ALTER COLUMN stock SET NOT NULL,
      ALTER COLUMN stock SET DEFAULT 0;
  ```
- **To add a unique constraint**, find and resolve duplicates first, or the migration fails.
- **To rename, use `RENAME`, never drop and re-add.** Drop-and-add silently deletes the data.
- **To move data between columns or tables**, add the new structure, copy with `INSERT ... SELECT` / `UPDATE ... FROM`, and drop the old structure only in a later migration, once no code reads it.
- **Destructive drops** of columns or tables go in their own migration, shipped once the code no longer uses them. The data is gone for good, so double-check.
- Data updates must be **deterministic**. Don't depend on row order, `NOW()`-sensitive logic or ids that differ between environments.

## 6. Seed and reference data

- **Reference data the application needs to work**, such as roles, statuses and lookup values, belongs in a versioned migration. Later changes to it are new migrations too.
- **Sample and dev data** (fake users, demo content) shouldn't ship to production. Keep it out of versioned migrations. Create test data in tests or through a dev-only mechanism instead.
- Never hardcode generated ids. Look rows up by natural key:
  ```sql
  INSERT INTO user_roles (user_id, role_id)
  SELECT u.id, r.id FROM users u, roles r
  WHERE u.username = 'admin' AND r.name = 'ADMIN';
  ```
- Make seed inserts safe against existing rows where that matters: `ON CONFLICT (name) DO NOTHING`.

## 7. Hygiene

- **No commented-out SQL.** Don't leave "maybe later" blocks in a migration. Put future work in its own migration when it's actually needed.
- Add a short `--` comment only where the *why* isn't obvious, such as a defensive backfill or a deliberate `ON DELETE` choice.
- Avoid `DROP ... CASCADE` unless you've checked exactly what it drops.
- Database-specific syntax (partial indexes, `BIGSERIAL`, `USING`) is fine for the target database. If another database runs the same scripts (for example an H2 profile for local runs or tests), check that the migration works there too.
- Don't put `BEGIN`/`COMMIT` in the file. Flyway already wraps each migration in a transaction.
- Statements that can't run in a transaction, such as `CREATE INDEX CONCURRENTLY`, need their own migration and Flyway's non-transactional handling. Avoid them unless a table is large enough to need them.

## 8. Keep the code in sync

A schema change is never SQL-only. In the **same change**:
- Update the entity (see `write-entity`): `nullable`, `length`, `unique` and column names must match the migration exactly.
- Update DTOs and mappers when the field crosses the API boundary (see `write-dto` and `write-mapper`).

## 9. Verify

1. Start the app against a local database (`mvn spring-boot:run`). The log should show `Migrating schema "public" to version "N - description"` and then a successful start.
2. Check the history:
   ```sql
   SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;
   ```
3. Exercise the affected endpoints to confirm the entity mapping still matches the table.
4. If the migration changes data, run it against a database that has realistic rows, not only an empty one.

If a migration **fails** partway, PostgreSQL rolls back its DDL. Fix the file (it was never applied successfully), clear the failed row from your *local* `flyway_schema_history` if one was recorded, and rerun.

## 10. Checklist

- [ ] New file `V{next}__{snake_case_description}.sql`, with the next number found by numeric sort
- [ ] No existing migration edited, renamed or deleted
- [ ] Audit columns on new `Auditable` tables, `BIGSERIAL` ids, `VARCHAR(255)` enums
- [ ] Named FKs with a deliberate `ON DELETE`, and an index on each FK column
- [ ] Existing rows handled: backfill before `NOT NULL` or `UNIQUE`, `RENAME` instead of drop/add
- [ ] No hardcoded generated ids, no commented-out SQL, no `BEGIN`/`COMMIT`
- [ ] Entity, DTOs and mapper updated to match
- [ ] App starts and migrates cleanly against a local database
