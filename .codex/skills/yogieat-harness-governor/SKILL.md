---
name: yogieat-harness-governor
description: Use when repeated Yogieat prompts, review comments, or misunderstandings should be translated into harness candidates or stable rules. Classifies new observations, applies the candidate-to-promotion policy, and keeps harness updates intentional rather than noisy.
---

# Yogieat Harness Governor

Use this skill when the conversation reveals a repeated instruction, a recurring review finding, or a stable preference that should live in the harness.

## Workflow

1. Read `./AGENTS.md`, `.codex/rules/governance.md`, and `.codex/governance/rule-candidates.md`.
2. Classify the new observation as:
   - already covered by a stable rule
   - new candidate rule
   - candidate ready for promotion
   - task-local preference that should stay out of the harness
3. Prefer candidate registration before promotion unless the miss is high severity.
4. During unrelated feature work, propose harness updates instead of silently editing them.
5. During dedicated harness work, update the relevant rule, skill, agent, or governance file directly.

## Good Candidates

- Repeated service or facade boundary clarifications
- Repeated validation scope corrections
- Repeated transaction or test harness pitfalls
- Repeated long prompt boilerplate that a skill or rule can replace

## Bad Candidates

- One-off naming tastes
- Isolated wording preferences
- Non-recurring refactor choices

## Notes

- Promotion threshold: two repeats, or one high-risk miss.
- Stable rules should stay short and operationally meaningful.
