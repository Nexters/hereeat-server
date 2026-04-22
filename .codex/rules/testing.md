# Yogieat Testing Rules

## Core Policy

- Test behavior, not implementation details.
- Favor assertions on returned values, saved state, emitted records, or HTTP responses.
- Let pure refactors pass without forcing test rewrites.
- New tests and touched tests must follow these rules. Existing tests are migrated incrementally.

## Mock Boundaries

- Mocking is allowed for:
  - database and repository boundaries
  - third-party HTTP or infrastructure clients
  - filesystem, clock, randomness, network, or process boundaries
- Avoid mocking or spying on:
  - entities, value objects, DTOs, pure utilities, and policies
  - internal collaborators in the same codebase when a real instance is practical
  - the unit under test
- If a test needs heavy internal mocking to be writable, re-check the test boundary before adding more mocks.

## Assertions

- Prefer one behavior per test.
- Prefer whole-object, collection, or state assertions over call-by-call verification.
- `verify(...)` is allowed only as a secondary assertion for a boundary side effect that cannot be expressed via returned data or captured saved state.
- Avoid `ArgumentCaptor` when local captured state or returned objects can prove the same behavior more directly.
- Do not use snapshots for non-deterministic output.

## Layer Guidance

- Pure policy, strategy, entity, or utility:
  - plain JUnit plus real objects
- Domain service, facade, processor, validator:
  - real internal collaborators where practical
  - mock only repositories and external boundaries
- Controller or API slice:
  - prefer `@WebMvcTest` or `MockMvcBuilders.standaloneSetup`
- Full integration behavior:
  - use `@SpringBootTest` only when DB, transaction, scheduler, filter, or wider wiring is part of the behavior
- `DatabaseCleaner` is for integration-test isolation only. Do not use it to recreate production bootstrap behavior or hidden seed paths.

## Naming And Shape

- Test names must describe observable behavior.
- Avoid names like `shouldWork`, method-name mirrors, or implementation verbs such as `callsX`.
- Prefer the template:
  - `<subject>_<expected_behavior>_when_<condition>`
- Prefer fixture builders or object mother style helpers over many small private flow helpers.

## Repo-Specific Guidance

- Keep current good patterns:
  - controller slice tests
  - H2/JPA-backed integration tests
  - AssertJ-centered assertions
- High-value smells to catch in review:
  - `verify(...)` as the only real proof
  - internal collaborator `@Spy`
  - startup side effects used as implicit seed data
  - test files that mostly mirror current call structure

## Incremental Migration

- Do not rewrite the whole test suite for style alone.
- Apply these rules to:
  - every new test
  - every edited test file
  - pilot refactors chosen for the harness
- Use future pilot refactors to target verify-heavy or spy-heavy tests first.
