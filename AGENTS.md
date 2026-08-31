# OnFilm agent work rules

This file applies to the entire OnFilm API repository.

## Database schema changes

### Database ownership

- This repository owns only the `onfilm_api` logical database and its Flyway migrations.
- The Encoding Worker owns `onfilm_worker` and `media_encode_inbox` in its own repository.
- Do not add cross-database foreign keys, joins, views, repositories, or transactions between API and Worker databases.
- Exchange API and Worker data only through the versioned Kafka message and authenticated Callback API contracts.
- Do not create databases or grant users in application Flyway migrations. Infrastructure initialization owns database and account provisioning.
- Follow [the API and Worker database ownership policy](docs/decisions/api-worker-database-ownership-and-flyway-baseline-policy.md).

### Schema source of truth

- Flyway versioned SQL migrations are the schema source of truth after the initial `V1` migration is introduced.
- Hibernate must validate mappings with `ddl-auto: validate`; it must not create, update, or drop shared schemas.
- H2 is not evidence that a schema change works on MySQL. Verify persistence behavior on the MySQL Testcontainers environment.
- Until `V1` is introduced, include every new mapping change in the pending `V1` design and do not add new reliance on `create`, `create-drop`, or `update`.
- Do not enable Flyway `baselineOnMigrate` for this project. The initial migration targets an empty database because there is no production data to preserve.

### Changes that require migration review

Treat a change as a database schema change when it affects any of the following:

- Entity, table, column, identifier, or generation strategy
- SQL type, length, precision, scale, nullability, default value, or enum representation
- Association, join column, collection table, order column, or cascade-dependent database constraint
- Unique, check, foreign-key, or index definition
- Optimistic-lock version column
- Persisted reference data or data transformation
- Native SQL or a repository query that depends on a new index

Pure Java validation or behavior changes do not require a migration only when the persisted representation remains unchanged. State that reasoning in the handoff when it may not be obvious.

### Migration rules

- Store API migrations under `src/main/resources/db/migration`.
- Name versioned migrations `V<version>__<snake_case_description>.sql`.
- Use a new, monotonically increasing version for every later change.
- Never edit or rename a committed migration that may have been applied. Add a corrective migration instead.
- Keep API and Worker version numbers independent; never place Worker DDL in this repository.
- Write MySQL-compatible SQL and test it on the project-pinned MySQL version.
- Do not qualify table names with `onfilm_api.` inside migrations. The configured datasource selects the target logical database.
- Make column nullability and important defaults explicit instead of relying on implicit database behavior.
- Give constraints and indexes stable names using `uk_`, `fk_`, `ck_`, and `idx_` prefixes.
- State the intended `ON DELETE` behavior for every foreign key. Do not infer cascade behavior solely from JPA cascade settings.
- Use a Unique Constraint for invariants that must remain true under concurrent requests; service-level duplicate checks alone are insufficient.
- Add an index only for a demonstrated query, constraint, join, or ordering requirement. Record the query and compare `EXPLAIN` before and after performance indexes.

### Entity and migration consistency

- Change the JPA mapping and its Flyway migration in the same work unit.
- Keep column names, lengths, nullability, enum storage, constraints, and indexes consistent between the entity and SQL.
- Review both sides of bidirectional relationships and the aggregate deletion policy before changing a foreign key.
- Do not use JPA `cascade` or `orphanRemoval` as a substitute for deciding the database foreign-key delete policy.
- Do not introduce a database foreign key for an intentionally weak ID-only relationship without revisiting and documenting that decision.

### Reference data and fixtures

- Keep production reference data separate from local and test fixtures.
- Put only data required in every environment in a reviewed migration or the approved reference-data initializer.
- Do not place sample users, movies, credentials, tokens, storage keys, or large demo datasets in production migrations.
- Tests must create their own data and must not depend on local `data.sql` contents.

### Required verification

For a schema change, verify the applicable items before reporting completion:

1. Apply all migrations from an empty MySQL database.
2. Start the Spring context with Hibernate `ddl-auto: validate`.
3. Run the affected repository and transaction tests on MySQL Testcontainers.
4. Add a rejection test for new Unique, Check, Not Null, or foreign-key constraints.
5. Add a concurrent-request test when correctness relies on uniqueness or locking.
6. Run `./gradlew test` and `./gradlew integrationTest`; use `./gradlew check` for the final repository-wide verification.
7. For index changes, capture comparable `EXPLAIN` or `EXPLAIN ANALYZE` evidence using the same query and dataset.

If the MySQL Testcontainers or Flyway environment required by these checks has not been introduced yet, report that limitation explicitly instead of treating an H2 result as final verification.

### Safety and documentation

- Never store database passwords or real connection strings in Git. Use environment variables or secrets and commit only safe examples.
- Do not assume that a database remains disposable merely because it currently has no production data. Confirm the target environment before a destructive migration or reset.
- Separate destructive or data-rewriting migrations from unrelated application changes and document backup, verification, and recovery steps.
- Update the database ownership policy when moving a table between services or changing the logical/physical database topology.
- Record the reason and trade-off for non-obvious constraints, nullable columns, weak relationships, and composite-index column order.
