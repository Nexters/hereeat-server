# Yogieat Harness Rule Candidates

## Promotion Threshold

- Promote after two repeats, or after one high-severity miss with clear operational or architectural risk.

## Candidate Template

| Date | Trigger | Candidate Rule | Status | Notes |
| --- | --- | --- | --- | --- |
| YYYY-MM-DD | Repeated prompt or review finding | Short rule statement | candidate | Why it matters |

## Active Candidates

| Date | Trigger | Candidate Rule | Status | Notes |
| --- | --- | --- | --- | --- |
| - | - | None yet | - | Add candidates here during harness work or explicit governance updates |

## Common Testing Candidates

- `verify(...)` is the only real assertion in a test
- internal collaborator `@Spy` appears in a domain test without a strong boundary reason
- test data depends on startup bootstrap or hidden global reset side effects
- non-deterministic output is checked with a snapshot instead of structural assertions
