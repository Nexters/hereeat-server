---
name: yogieat-java25-gradle-validation
description: Use when validating Yogieat changes or choosing the smallest Java 25 Gradle proof command. Applies Spotless first, then compile, then module-local or full tests based on repo-specific escalation rules.
metadata:
  short-description: Validate Yogieat changes with Java 25 and Gradle
---

# Yogieat Java 25 Gradle Validation

Use this skill when you need the smallest validation command that still proves a Yogieat change is safe.

## Workflow

1. Read `./AGENTS.md` and `.codex/rules/validation.md`.
2. Run `./gradlew spotlessApply --daemon -q` first.
3. Run `./gradlew compileJava --daemon -q`.
4. Choose the smallest matching test scope.
5. Escalate to `./gradlew test --daemon` for storage, public API, shared contract, root build, or mixed multi-module changes.

## Commands

- All changes:
  - `./gradlew spotlessApply --daemon -q`
  - `./gradlew compileJava --daemon -q`
- Domain-only:
  - `./gradlew :apps:domain:test`
- Batch-only:
  - `./gradlew :batch:sync:test`
- External-only:
  - `./gradlew :external:ai:test`
  - `./gradlew :external:kakao:test`
- Support-only:
  - `./gradlew :support:logging:test`
  - `./gradlew :support:monitoring:test`
  - `./gradlew :support:swagger:test`
- Escalated scope:
  - `./gradlew test --daemon`

## Notes

- `scripts/pre-commit` is the source of truth for the default formatting and compile steps.
- `.codex/hooks/verify.sh` wraps the same policy for current git changes.
