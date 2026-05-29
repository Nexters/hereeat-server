# Yogieat Codex Guide

## Source Of Truth

- Repo architecture: `docs/architecture/multi-module-structure.md`
- Validation defaults: `scripts/pre-commit`, `build.gradle`, `gradle.properties`
- Repo-local harness rules: `.codex/rules/*.md`
- Testing guidance: `.codex/rules/testing.md`
- Repo-local skills and agents: `.codex/skills/`, `.codex/agents/`
- Harness dry-runs: `_workspace/evals/`
- Keep active Markdown guidance around 100 lines; move detail into focused rules or skills.

## Architecture

- `apps:api`: public API controllers, request or response DTOs, API wiring
- `apps:admin`: admin API controllers, request or response DTOs, admin wiring
- `apps:domain`: business logic, orchestration, repository interfaces, validators, processors
- `storage:db-core`: JPA entities, QueryDSL, storage implementations
- `external:*`: external API and infrastructure clients
- `support:*`: cross-cutting infrastructure only
- `batch:sync`: batch entrypoints and scheduling

## Stable Conventions

- Controller는 Facade만 호출하고, Facade는 조합/오케스트레이션, Service는 도메인 내부 로직을 맡는다.
- Processor/Creator/Analyzer/Resolver/Validator는 이름 있는 책임이 분명할 때만 도입한다.
- collaborator가 늘어나면 의존성 주입보다 책임 분리를 먼저 검토한다.
- 선언형 트랜잭션이 필요하면 self-invocation을 피하고 별도 빈으로 분리한다.
- Flyway migration은 적용 후 불변 이력이다. 공유 DB 적용 여부를 먼저 확인하고 보정은 다음 version으로 처리한다.
- 테스트는 hidden bootstrap/reset보다 test-local fixture/setup을 우선한다.
- API는 리소스 중심 URI와 `201 Created` 생성 응답을 기본으로 한다.

## Validation Defaults

- Java toolchain: 25
- Spring Boot: 3.5.9
- Spotless: 7.0.2
- 기본 종료 루틴: `./gradlew spotlessApply --daemon -q`, `./gradlew compileJava --daemon -q`, 최소 관련 테스트.
- `storage/**`, public API main code, shared contract, mixed multi-module changes는 `./gradlew test --daemon`으로 승격한다.
- Flyway migration 변경은 full test 승격 대상이며, migration version 충돌과 공유 DB applied history 누락 여부를 함께 검토한다.
- Repo-local validation helper: `.codex/hooks/verify.sh`

## Testing Conventions

- Assert behavior, return values, and observable state before internal call structure.
- Mock only at boundaries; avoid spies on internal collaborators when real objects are practical.
- `verify(...)` is secondary proof only when returned data or saved state cannot show the side effect.
- Prefer plain JUnit for pure policies and `@WebMvcTest` or standalone MockMvc for controller slices.
- Use `@SpringBootTest` only when DB, transaction, scheduler, filter, or wider wiring matters.
- Follow `.codex/rules/testing.md` for detailed test writing and review guidance.

## Agent Routing Defaults

- Every prompt should be checked against `.codex/rules/agent-routing.md` before work starts.
- Default policy: always analyze candidate agents, but keep trivial work on the main thread.
- Recommend agents before spawning them unless the user explicitly asks for delegation or parallel review.
- Use hub-and-spoke collaboration. The main thread coordinates; child agents stay focused on one concern.

## Auto-Loading

- Run `codex -C .` from the repository root.
- Codex automatically loads project-scoped skills from `.codex/skills/` and custom agents from `.codex/agents/`.
- Personal global skills can coexist, but repo-local guidance should be the default for Yogieat work.
- `.codex/config.toml` enables repo-local multi-agent defaults for this project.

## Custom Agents

- `feature_mapper`: map affected modules, entrypoints, and validation scope before feature work.
- `architecture_guard`: review layers, boundaries, DI shape, transaction placement, and naming conventions.
- `validation_triager`: choose Java 25, Gradle, Spotless, compile, test, or CI parity scope.
- `harness_curator`: inspect repeated prompts, review comments, or candidate rule promotions.

## Repo-Local Skills

- `yogieat-agent-router`: classify prompts and decide whether repo-local agents are useful.
- `yogieat-feature-scaffold`: add features without drifting from module/component conventions.
- `yogieat-clean-architecture-guard`: guard boundaries, DI shape, validator extraction, and naming.
- `yogieat-testing-guard`: write/review behavior-first, boundary-mocking tests.
- `yogieat-java25-gradle-validation`: choose the smallest proof command.
- `yogieat-harness-governor`: turn repeated observations into candidate or stable rules.

## Usage Rules

- Do not use subagents for trivial single-file edits.
- Prefer read-only agents for exploration, architecture review, and harness review.
- Keep public API mapping in `apps:api` or `apps:admin`, business logic in `apps:domain`, and implementations in `storage`, `external`, or `support`.
- Reuse repo-local skills before restating the same long prompt pattern by hand.
- When a review comment or repeated instruction reveals a stable preference, evaluate whether it belongs in `.codex/governance/rule-candidates.md` or `.codex/rules/*.md`.
