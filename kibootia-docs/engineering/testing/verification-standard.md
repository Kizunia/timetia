# Verification Standard

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

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

## CI pipeline (owner decisions of 2026-10-28; none of it exists yet)

- **Platform:** GitHub Actions.
- **On every pull request:** backend build with unit, ArchUnit and Testcontainers integration tests (including migrate-from-empty, tenant isolation and soft-delete filtering); frontend lint, type check and unit tests; the module-removal job ([ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md)); secret scanning.
- **Images:** built and scanned for known vulnerabilities.
- **Dependencies:** automated grouped update pull requests, with security updates first ([ADR 0035](../../decisions/0035-version-adoption-policy.md)).
- **End-to-end tests:** nightly, before release tags, and on pull requests labelled `e2e`.
- **Workflow:** trunk-based, with short-lived branches and pull requests into `main`. Commit messages follow Conventional Commits, which generates the changelog. Releases are SemVer tags ([ADR 0023](../../decisions/0023-template-fork-adoption-model.md)).

## Definition of done for a feature

Owner decision of 2026-11-08. A feature is done when CI is green on its pull request and:

1. **Authorization:** every protected use case has an allowed and a denied test; list endpoints test their scope ([authorization](../../architecture/identity/authorization.md)).
2. **Backend tests:** unit tests for logic; Testcontainers integration tests for persistence, including tenant isolation and soft-delete filtering where the module has tables; API tests for every endpoint, including its error cases. Spring Modulith and ArchUnit verification pass.
3. **Migrations** live in the module's folder, and the migrate-from-empty test passes.
4. **Contract:** `openapi.json` is updated and committed, and the frontend client is regenerated ([API conventions](../../architecture/api/conventions.md)).
5. **Frontend:** permission-aware UI; Vitest tests for non-trivial logic; for user-facing features, a Playwright happy path and one denied path with an automated accessibility (axe) check.
6. **Safety:** no secrets or personal data in logs; a [threat model](../../operations/security/threat-model.md) entry for any new trust boundary; rate limiting for sensitive actions.
7. **Documentation:** affected documents, the [configuration catalogue](../../operations/configuration/application-configuration.md) and [implementation status](../../product/roadmap/implementation-status.md) are updated, and an ADR exists for any architectural choice.

Coverage is reported, with no percentage gate.

## Not decided

Formatting and link-checking jobs. See [open questions](../../decisions/open-questions.md).
