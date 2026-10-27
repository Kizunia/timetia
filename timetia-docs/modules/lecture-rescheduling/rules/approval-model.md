# Approval model

- **Type:** rules
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

How a ticket gets approved. Original design: [module spec](../../lecture-rescheduling.md), section 9. Where this file differs from the spec, this file wins.

## Self-approval

A faculty member can apply their own change directly. **No case forces a review**, whether the change is at short notice, in an exam week or involves a shared course. A review is always the author's choice. See [self-approval](../../../shared/glossary/self-approval.md).

## Optional reviewer checklist

The author may request review from any combination of: the HOD, the CR/BR within their scope, the affected faculty, other faculty.

## Consent for exchange and substitute is optional

Faculty have the right to exchange and substitute lectures. Asking the other faculty for consent is a choice, because they have often already agreed in person.

| Situation | What happens |
| --- | --- |
| Author asks for consent | The other faculty accepts or rejects as a reviewer. |
| Author does not ask | The change applies once the conflict check passes. The other faculty is notified and may [revert it](reverts.md) before the lecture. |

An exchange still applies both moves together or neither. Rescheduling into a slot where the students have no lecture is allowed.

## Permanent changes

Always need the HOD. See [permanent changes](permanent-changes.md).

## Review timeout and expiry

There is **no escalation**. If a requested reviewer does not respond, the ticket waits. It **expires when the lecture starts**.

## Rejection

- A faculty reviewer, the HOD, and a CR or BR within their scope ([permissions](permissions.md)) can reject. A rejection needs a reason.
- The author can escalate a rejection to the HOD, or edit and resubmit.

## Always logged

Every action is written to the audit log: who, what, when, from which state. Conflicts are checked when the ticket is raised and again at approval.
