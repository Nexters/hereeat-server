# Yogieat Agent Routing Rules

## Default Policy

- Analyze candidate agents for every prompt before doing substantial work.
- Default to local execution for trivial or tightly scoped work.
- Recommend agents before spawning them unless the user explicitly asks for delegation, subagents, or parallel review.
- Use hub-and-spoke coordination. The main thread decomposes the task, child agents focus on one concern, and the main thread merges results.

## Candidate Mapping

- `feature_mapper`
  - new feature
  - change-impact mapping
  - multi-module pathfinding
  - DB schema or Flyway migration impact mapping
- `yogieat-testing-guard` skill first
  - test writing
  - test refactor
  - test review
  - mock boundary choice
  - assertion style choice
- `architecture_guard`
  - service vs facade placement
  - validator extraction
  - DI shape
  - transaction boundary or component naming drift
  - test tasks that also question layer or transaction boundaries
- `validation_triager`
  - Java 25
  - Gradle scope
  - Spotless, compile, test, CI parity
  - module-local vs full-test decisions
  - Flyway migration version or applied-history validation risk
- `harness_curator`
  - repeated prompt patterns
  - recurring review comments
  - repeated misunderstandings
  - candidate or stable rule promotion
  - repeated testing review feedback

## Flyway Prompt Preflight

- When a prompt asks for a DB schema change, Flyway migration, migration repair, or migration version rename, stop before editing migration files and ask one short confirmation question:
  - whether any shared dev/prod DB or active branch already has an applied migration version or filename that could overlap.
- If the user already provided the applied version context, proceed using that information and state the assumption.
- If overlap exists, restore the exact applied migration first and place new work in the next migration version.

## Spawn Guidance

- Do not spawn agents for trivial single-file edits or straightforward factual questions.
- Spawn or recommend only the smallest useful set of agents.
- If multiple review axes matter, prefer parallel specialists over one broad generic reviewer.
- Keep one main writer. Use review or mapping agents as read-only guides.

## Collaboration Shape

- Each agent should get one crisp concern and one output contract.
- The main thread is responsible for:
  - task decomposition
  - conflict resolution
  - merging findings
  - final recommendation
- Do not let multiple agents propose overlapping ownership unless the work is intentionally comparative.
