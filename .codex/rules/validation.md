# Yogieat Validation Rules

## Repo Facts

- Java toolchain: 25
- Spring Boot: 3.5.9
- Spotless: 7.0.2
- Source of truth:
  - `build.gradle`
  - `gradle.properties`
  - `scripts/pre-commit`

## Default Sequence

1. `./gradlew spotlessApply --daemon -q`
2. `./gradlew compileJava --daemon -q`
3. The smallest relevant Gradle test task

## Full Test Escalation

Run `./gradlew test --daemon` when the diff touches any of the following:

- `storage/**`
- public API main code in `apps:api` or `apps:admin`
- shared contract or domain-wide interface changes
- shared test infrastructure or fixtures used across multiple tests or modules
- root Gradle or build configuration
- mixed multi-module runtime changes

## Smallest Useful Proof Commands

- `apps:domain` only:
  - `./gradlew :apps:domain:test`
- `batch:sync` only:
  - `./gradlew :batch:sync:test`
- `external:ai` only:
  - `./gradlew :external:ai:test`
- `external:kakao` only:
  - `./gradlew :external:kakao:test`
- `support:*` only:
  - `./gradlew :support:<module>:test`
- test-only change in a single module:
  - `./gradlew :<module>:test`

If the change matches the full-test escalation rules, do not stay on module-local validation.

## Test-Specific Guidance

- For test-only changes inside one module, keep the proof at that module's test task after Spotless and compile.
- If the diff changes shared test utilities, shared fixtures, or tests in multiple modules, escalate to `./gradlew test --daemon`.

## Hook

- Use `.codex/hooks/verify.sh` as the repo-local wrapper around these rules.
- The hook should detect changed modules from git diff and choose module-local tests or `./gradlew test --daemon` accordingly.
