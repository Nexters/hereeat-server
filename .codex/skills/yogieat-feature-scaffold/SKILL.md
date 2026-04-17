---
name: yogieat-feature-scaffold
description: Use when adding or extending a Yogieat feature in the Java 25 multi-module Spring Boot architecture. Maps the request to the smallest consistent slice across `apps:api/admin`, `apps:domain`, `storage`, `external`, and `support` while preserving the current Service, Facade, Processor, and Validator conventions.
metadata:
  short-description: Add a Yogieat feature without architectural drift
---

# Yogieat Feature Scaffold

Use this skill when implementing a new feature or extending an existing flow in Yogieat.

## Workflow

1. Read `./AGENTS.md` and `docs/architecture/multi-module-structure.md`.
2. Map the request to the smallest affected module set.
3. Mirror the nearest existing feature slice before introducing a new package or component pattern.
4. Decide the component boundary first:
   - Facade for multi-domain orchestration
   - Service for single-domain logic
   - Validator for non-trivial domain validation
   - Processor or Creator for a named subflow that deserves its own role
5. Keep adapters thin in `storage`, `external`, and `support`.
6. Pick the smallest validation scope using the Java 25 validation skill.

## Rules

- Controllers stay in `apps:api` or `apps:admin`.
- Request and response DTOs stay in the app module that owns the endpoint.
- Business logic, repository interfaces, validators, processors, commands, and criteria stay in `apps:domain`.
- JPA and QueryDSL implementations stay in `storage:db-core`.
- External client implementations stay in `external:*`.
- Do not put cross-domain orchestration into a service.
- Do not add private helper chains when the logic should become a validator or processor.

## Output

- Affected modules
- Intended component types
- Likely entrypoints
- Validation scope
