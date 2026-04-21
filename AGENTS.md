# Yogieat Codex Guide

## Source Of Truth

- Repo architecture: `docs/architecture/multi-module-structure.md`
- Validation defaults: `scripts/pre-commit`, `build.gradle`, `gradle.properties`
- Repo-local harness rules: `.codex/rules/*.md`
- Testing guidance: `.codex/rules/testing.md`
- Repo-local skills and agents: `.codex/skills/`, `.codex/agents/`

## Architecture

- `apps:api`: public API controllers, request or response DTOs, API wiring
- `apps:admin`: admin API controllers, request or response DTOs, admin wiring
- `apps:domain`: business logic, orchestration, repository interfaces, validators, processors
- `storage:db-core`: JPA entities, QueryDSL, storage implementations
- `external:*`: external API and infrastructure clients
- `support:*`: cross-cutting infrastructure only
- `batch:sync`: batch entrypoints and scheduling

## Stable Conventions

- Controller는 Facade만 호출한다. Controller가 Service를 직접 호출하지 않는다.
- Service는 본 도메인 내부 비즈니스 로직만 담당한다.
- Facade는 여러 도메인 조합과 오케스트레이션만 담당한다.
- Processor, Creator, Analyzer, Resolver는 이름 있는 하위 책임이 분명할 때만 도입한다.
- Validator는 도메인 중심 컴포넌트로 분리하고 의존성 주입으로 사용한다.
- private 메서드는 복잡도 감소가 분명할 때만 허용한다. 단순 흐름 분해용 helper 남용은 지양한다.
- 의존성 주입은 최소화한다. collaborator가 늘어나면 책임 분리를 먼저 검토한다.
- 선언형 트랜잭션 의미가 필요한 로직은 self-invocation 을 금지하고 별도 빈으로 분리한다.
- 임시 migration, bootstrap, initializer, backfill 코드는 제거 시점이 명확해야 한다.
- 테스트는 startup side effect 나 전역 magic reset 보다 test-local fixture 또는 setup 을 우선한다.
- 새로운 구조를 만들기 전에 가장 가까운 기존 feature slice 를 먼저 따른다.
- HTTP API는 화면명이나 구현 목적보다 리소스 중심 URI를 우선한다. `dashboard`, `screen`, `page` 같은 view-oriented 경로는 지양한다.
- 컬렉션 조회에 집계나 관리용 필드가 필요하면 별도 view endpoint보다 해당 리소스 표현이나 query parameter 확장을 먼저 검토한다.
- 새 리소스 생성 후 안정적인 단건 조회 URI가 있다면 `POST` 응답은 `201 Created` 와 `Location` 헤더를 우선한다.

## Validation Defaults

- Java toolchain: 25
- Spring Boot: 3.5.9
- Spotless: 7.0.2
- 기본 종료 루틴:
  - `./gradlew spotlessApply --daemon -q`
  - `./gradlew compileJava --daemon -q`
  - 최소 관련 테스트
- 아래 변경은 `./gradlew test --daemon` 으로 승격한다.
  - `storage/**`
  - public API main code
  - shared contract
  - mixed multi-module changes
- Repo-local validation helper:
  - `.codex/hooks/verify.sh`

## Testing Conventions

- Test behavior, return values, and observable state before internal call structure.
- Mock only at boundaries such as DB, external HTTP, filesystem, clock, randomness, network, or process boundaries.
- Avoid mocking or spying on internal collaborators in the same codebase when a real object is practical.
- `verify(...)` is not the primary proof. Use it only as a secondary check for a boundary side effect that cannot be asserted through returned data or saved state.
- Prefer plain JUnit plus real objects for pure policies, strategies, and entities.
- Prefer `@WebMvcTest` or `MockMvcBuilders.standaloneSetup` for controller slices.
- Use `@SpringBootTest` only when DB, transaction, scheduler, filter, or wider wiring behavior is part of the behavior under test.
- Keep test seed data local to the test or fixture. Do not rely on startup bootstrap side effects.
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

- `feature_mapper`
  - Use when starting a feature or change and you need affected modules, entrypoints, and validation scope mapped first.
- `architecture_guard`
  - Use when a task mixes layers, component boundaries, DI shape, transaction placement, or naming conventions.
- `validation_triager`
  - Use when Java 25, Gradle, Spotless, compile, test, or CI parity scope is unclear.
- `harness_curator`
  - Use when repeated prompts, review comments, or recurring misunderstandings may need a harness rule update.

## Repo-Local Skills

- `yogieat-agent-router`
  - Classify prompts and decide whether the work stays local or should consider repo-local agents.
- `yogieat-feature-scaffold`
  - Add new features without drifting from Yogieat module and component conventions.
- `yogieat-clean-architecture-guard`
  - Guard layer boundaries, DI shape, validator extraction, and component naming choices.
- `yogieat-testing-guard`
  - Write and review tests with behavior-first assertions and boundary-only mocking.
- `yogieat-java25-gradle-validation`
  - Choose the smallest Java 25 Gradle validation command that proves a change is safe.
- `yogieat-harness-governor`
  - Turn repeated instructions or misunderstandings into candidate or stable harness rules.

## Quick Commands

- Open an interactive Codex session:
  - `codex -C .`
- Ask `feature_mapper` to map a change:
  - `codex exec -C . "Spawn feature_mapper to map the affected modules, likely entrypoints, validators or processors to extract, and the smallest validation scope. Wait for it and respond in Korean."`
- Ask `architecture_guard` to review boundaries:
  - `codex exec -C . "Spawn architecture_guard to review this change for facade or service boundary drift, validator extraction gaps, DI bloat, transaction placement, and unnecessary private helpers. Wait for it and summarize only concrete findings in Korean."`
- Ask `validation_triager` to choose validation:
  - `codex exec -C . "Spawn validation_triager to choose the smallest Java 25 Gradle validation scope for the current diff. Explain whether this change needs module-local tests or ./gradlew test --daemon. Respond in Korean."`
- Ask `harness_curator` to inspect recurring instructions:
  - `codex exec -C . "Spawn harness_curator to inspect this conversation or diff for repeat instructions, repeated review comments, and candidate harness rule promotions. Do not edit files. Respond in Korean."`

## Usage Rules

- Do not use subagents for trivial single-file edits.
- Prefer read-only agents for exploration, architecture review, and harness review.
- Keep public API mapping in `apps:api` or `apps:admin`, business logic in `apps:domain`, and implementations in `storage`, `external`, or `support`.
- Reuse repo-local skills before restating the same long prompt pattern by hand.
- When a review comment or repeated instruction reveals a stable preference, evaluate whether it belongs in `.codex/governance/rule-candidates.md` or `.codex/rules/*.md`.
