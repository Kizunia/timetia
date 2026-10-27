# Verification Standard

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. There is no CI pipeline, so no behavior in the repository can currently be called verified.

Decision: [ADR 0018](../../decisions/0018-ci-as-verification-standard.md).

## Rule (owner decision)

Behavior is described as **verified**, and a milestone as complete, only when automated tests covering it have been run by a CI pipeline on the change and passed. A local run alone is not enough.

## Wording

| Situation | Allowed wording |
|---|---|
| Decided, not in the repository | planned |
| In the repository, no passing CI run covers it | observed |
| Covered by tests that passed in CI | verified |

See the [vocabulary](../../meta/living-documentation.md).

## Recording evidence

When a capability becomes verified, update its row in [implementation status](../../product/roadmap/implementation-status.md) with the date and a reference to the CI run or the test that covers it. Do not write "tested" for a test that was not executed.

## Milestone completion

A milestone is complete when each of its criteria is verified under this rule. Criteria are listed in the [milestone files](../../product/roadmap/README.md).

## Not decided

The CI platform and pipeline layout, and what other checks (formatting, dependency scanning, link checking) the pipeline runs. See [open questions](../../decisions/open-questions.md).
