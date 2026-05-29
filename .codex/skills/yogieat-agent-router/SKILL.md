---
name: yogieat-agent-router
description: Use when deciding whether a Yogieat prompt should stay on the main thread or consider repo-local agents. Classifies prompts, maps them to `feature_mapper`, `architecture_guard`, `validation_triager`, or `harness_curator`, and keeps delegation intentionally minimal.
---

# Yogieat Agent Router

Use this skill before substantial work when you need to decide whether the task stays local or should consider repo-local agents.

## Workflow

1. Read `./AGENTS.md` and `.codex/rules/agent-routing.md`.
2. Classify the prompt as one of:
   - direct implementation
   - architecture decision
   - feature impact mapping
   - validation or CI triage
   - test writing or test review
   - harness or prompt-governance work
3. Decide whether the work is trivial enough to stay on the main thread.
4. If an agent would help, recommend the smallest useful set and define one concern per agent.
5. Default every user-facing recommendation or summary to Korean unless another language is explicitly requested.

## Routing Rules

- Use `feature_mapper` for impact mapping before new feature implementation.
- Use `architecture_guard` when the main ambiguity is service, facade, validator, processor, or transaction placement.
- Also use `architecture_guard` when the ambiguity is resource-oriented URI design, response shape ownership, or `POST` creation semantics.
- Use `validation_triager` when the main ambiguity is Java 25 Gradle validation scope or CI parity.
- Use `yogieat-testing-guard` before writing, refactoring, or reviewing tests.
- Use `harness_curator` when the task involves repeated prompts, repeated review comments, or harness rule promotion.

## Notes

- Do not recommend child agents for trivial single-file edits.
- Prefer one main writer and read-only specialist agents.
- Codex only creates child agents when you explicitly ask it to spawn them.
- Keep the default policy as: always analyze, recommend when helpful, spawn only when the user asks for delegation or parallel review.
