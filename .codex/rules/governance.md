# Yogieat Harness Governance

## Rule Classes

- Stable rules live in:
  - `AGENTS.md`
  - `.codex/rules/*.md`
- Candidate rules live in:
  - `.codex/governance/rule-candidates.md`

## Promotion Policy

- Promote a candidate to a stable rule when:
  - the same instruction or misunderstanding appears at least twice, or
  - a single miss creates a high operational or architectural risk
- Do not promote one-off stylistic preferences that do not materially change implementation or review quality.

## Update Policy

- Always evaluate whether the current conversation reveals:
  - a repeated request
  - a repeated misunderstanding
  - a recurring review finding
  - a repeated testing review comment that should become a stable rule
- During unrelated feature work, propose rule changes first instead of silently editing the harness.
- During dedicated harness work, update the rule files, skills, or agents directly.

## Candidate Scope

- Good candidates:
  - repeated component placement clarifications
  - repeated validation scope corrections
  - repeated transaction or test harness pitfalls
  - repeated testing feedback about mocking boundaries, `verify(...)`, or hidden seed behavior
  - repeated prompt boilerplate that can be replaced by a skill or routing rule
- Bad candidates:
  - one-off naming tastes
  - isolated wording preferences
  - incidental refactor choices that do not generalize

## Review Loop

- After reviews, postmortems, or repeated back-and-forth:
  - check whether the issue is already covered by a stable rule
  - if not, record it as a candidate
  - if it recurs, promote it
  - if it is a high-value testing review pattern, prefer promoting it into `.codex/rules/testing.md`
