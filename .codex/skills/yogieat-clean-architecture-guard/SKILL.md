---
name: yogieat-clean-architecture-guard
description: Use when deciding where new Yogieat code belongs or when reviewing architecture drift. Focuses on Service vs Facade boundaries, validator extraction, processor naming, transaction placement, dependency minimization, and preserving the existing multi-module structure.
metadata:
  short-description: Guard Yogieat layer boundaries and component roles
---

# Yogieat Clean Architecture Guard

Use this skill when the task is "where should this live?" or "does this still fit the project conventions?"

## Workflow

1. Read `./AGENTS.md` and `.codex/rules/architecture.md`.
2. Identify the smallest layer that owns the behavior.
3. Prefer the nearest existing feature slice over a new abstraction.
4. Check whether the logic belongs in a Facade, Service, Validator, Processor, or storage or external adapter.
5. Call out boundary drift before suggesting refactors.

## Review Focus

- Controller directly calling Service
- Cross-domain orchestration inside a Service
- Validator-worthy logic left inline inside Facade or Service
- Processor or Creator introduced without a real named responsibility
- Constructor dependency bloat
- Transactional self-invocation
- Missing `@Transactional(readOnly = true)` on read-only facade or service methods
- Write methods accidentally inheriting `readOnly = true` or missing a write transaction boundary
- Private helper sprawl that hides simple flow
- Temporary migration or bootstrap code left in runtime after completion
- View-oriented HTTP design such as `dashboard` endpoints for data that should still be a resource representation
- Missing item endpoint after `POST` resource creation when the feature needs canonical item access

## Notes

- Prefer concrete boundary corrections over abstract architecture advice.
- If the code is already close to a neighboring slice, refine it instead of reshaping the whole area.
