---
name: yogieat-testing-guard
description: Use when writing, refactoring, or reviewing Yogieat tests. Focuses on behavior-first assertions, boundary-only mocking, test layer choice, and smell detection for spy-heavy or verify-heavy tests.
metadata:
  short-description: Guard Yogieat tests against implementation-coupled drift
---

# Yogieat Testing Guard

Use this skill for any Yogieat prompt about writing, fixing, or reviewing tests.

## Workflow

1. Read `./AGENTS.md`, `.codex/rules/testing.md`, and `.codex/rules/validation.md`.
2. Choose the smallest test layer that proves the behavior.
3. Identify which collaborators are true system boundaries and which are internal objects that should stay real.
4. Prefer behavior-first assertions on returned data, saved state, or HTTP responses.
5. Run the smallest validation scope that matches the changed test surface.

## Layer Choice

- Pure policy, strategy, entity, validator logic:
  - plain JUnit plus real objects
- Domain service, facade, processor:
  - mock repositories and external boundaries only
  - keep internal policies and factories real when practical
- Controllers:
  - prefer `@WebMvcTest` or `MockMvcBuilders.standaloneSetup`
- Full integration:
  - use `@SpringBootTest` only when DB, transaction, filter, scheduler, or wiring behavior matters

## Review Focus

- `verify(...)` used as the main proof
- internal collaborator `@Spy`
- captor-heavy tests that could assert on saved or returned state instead
- startup bootstrap behavior used as hidden test seed
- test names that describe method calls instead of observable behavior

## Red Flags

- more mocking setup than real assertions
- tests that rename every time the implementation method shape changes
- snapshots for timestamps, ordering-free data, or generated text
- shared global fixtures used where test-local data would be clearer

## Notes

- Keep current good repo patterns such as AssertJ assertions, controller slice tests, and H2-backed integration tests.
- Do not start broad suite rewrites. Apply the rules to new tests, touched tests, and explicit pilot refactors.
