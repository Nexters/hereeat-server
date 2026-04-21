# Yogieat Architecture Rules

## Layer Ownership

- `apps:api` and `apps:admin` own HTTP entrypoints, request or response DTOs, security, and application wiring.
- `apps:domain` owns orchestration, business rules, repository interfaces, validators, processors, and domain-facing abstractions.
- `storage:db-core` owns JPA, QueryDSL, and storage implementations.
- `external:*` owns client implementations and infrastructure-facing translation.
- `support:*` is cross-cutting infrastructure only, not feature business logic.
- `batch:sync` owns batch entrypoints and schedule wiring.

## Component Conventions

- Controller -> Facade only
- Facade -> multiple domain coordination only
- Service -> single-domain business logic only
- Repository interface -> `apps:domain`
- Repository implementation -> `storage` or `external`
- Validator -> extracted domain validation component
- Processor, Creator, Analyzer, Resolver -> named subflow only, not a dumping ground

## Boundary Rules

- Do not let controllers call services directly.
- Do not move cross-domain orchestration into a service.
- Do not place feature business logic in `support:*`.
- Do not introduce a shared abstraction until at least two real slices need it.
- Mirror the nearest existing feature slice before inventing a new package pattern.
- Prefer resource-oriented HTTP design in `apps:api` and `apps:admin`.
- Avoid view-oriented endpoint names such as `dashboard`, `screen`, or `page` when the response is still a representation of an existing resource.
- If admin or UI needs counts or summary fields for a resource collection, prefer enriching the collection or item representation before adding a separate view-specific endpoint.
- When a `POST` creates a stable resource that has a canonical item URI, prefer `201 Created` plus a `Location` header that points to that item.

## Validator And Method Shape

- When validation spans more than a trivial null or blank check, prefer a domain validator component over a private validation block inside a facade or service.
- Prefer injected validators over duplicated validation logic.
- Keep private methods only when they clearly reduce complexity or isolate a meaningful algorithm.
- Do not split a short linear method into multiple private helpers just to make it look layered.

## Dependency Injection

- Keep constructor dependencies small and role-focused.
- If a class starts accumulating many collaborators, prefer extracting a processor, validator, or persistence helper rather than adding more injections.
- Favor loose coupling through domain interfaces and role-specific components.

## Transaction And Migration Rules

- If transactional semantics depend on proxying, self-invocation is not allowed.
- Use a dedicated bean for isolated transactional work such as `REQUIRES_NEW`.
- Temporary initializer, bootstrap, migration, or backfill code must have an explicit removal condition.
- Once a migration is complete, remove runtime migration paths instead of keeping them indefinitely.

## Test Rules

- Prefer test-local seed data and fixtures over startup magic or global reset side effects.
- If a test needs reference data, seed it in the test or the nearest fixture rather than reintroducing runtime bootstrap behavior.
