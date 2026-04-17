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
- `harness_curator`
  - repeated prompt patterns
  - recurring review comments
  - repeated misunderstandings
  - candidate or stable rule promotion
  - repeated testing review feedback

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
