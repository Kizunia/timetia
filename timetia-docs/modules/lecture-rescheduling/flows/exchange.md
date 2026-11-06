# Faculty exchanges lectures

- **Type:** flow
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-06

Migrated from the original [spec](../_archive/original-spec.md), section 8.3, and updated to the current [rules](../rules/README.md). Where they differ, the rules win.

1. Faculty A picks their occurrence and chooses **Exchange**.
2. A picks faculty B's occurrence from the division timetable.
3. Conflict checks run for both moves.
4. A chooses whether to ask B for consent. **Consent is optional**, because the two have often agreed in person. If A asks, B accepts or rejects as a reviewer. If A does not, the change applies once the checks pass, B is notified, and B may [revert it](../rules/reverts.md) before the lecture starts. Other reviewers (HOD, CR/BR) may be added as in the other flows.
5. On approval both moves apply together, or neither does.
