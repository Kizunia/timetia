# Changelog

- **Type:** reference
- **Module:** lecture-rescheduling
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

History of business-logic decisions, in the order they were taken with the owner. Each line links to the file that now holds the rule.

## 2026-10-27

**Approval, roles and conflicts**
- Consent for exchange and substitute is optional; the affected faculty can revert ([approval model](rules/approval-model.md), [reverts](rules/reverts.md)).
- No warning level; every conflict blocks; no overrides ([conflict rules](rules/conflict-rules.md)).
- No forced review; no escalation; tickets expire when the lecture starts; CR/BR can reject, escalation to HOD ([approval model](rules/approval-model.md)).
- CR for theory, BR for labs and tutorials; multiple CRs; no assign powers ([permissions](rules/permissions.md)).

**Reverts, same-slot tickets, cancellation, requests**
- Revert until the lecture starts, conflict-checked ([reverts](rules/reverts.md)).
- First approved ticket wins ([same-slot tickets](rules/same-slot-tickets.md)).
- Faculty cancels directly with a reason ([cancellation](rules/cancellation.md)).
- GitHub-style issues from CR and BR ([issues](rules/issues-from-cr-and-br.md)).

**Rooms, notifications, timetable versions**
- Author picks the room; lecture mode; meeting link optional ([rooms](rules/rooms-and-location.md)).
- In-app notifications only, no reminders ([notifications](notifications/triggers.md)).
- Keep and re-check changes on a new ERP version ([integration](integration/timetable-version-changes.md)).

**Shared courses, load, permanent changes, leave**
- Shared-course check skipped ([shared courses](rules/shared-courses.md)); extra lectures count toward load ([faculty load](rules/faculty-load.md)).
- Permanent changes applied by the module for now ([permanent changes](rules/permanent-changes.md)); HOD covers faculty on leave ([faculty on leave](rules/faculty-on-leave.md)).

## 2026-11-06 Ticket lifecycle

- Seven states: Draft, In review, Applied, Rejected, Withdrawn, Expired, Reverted. Open and Approved dropped.
- Conflicted tickets can be reviewed but not applied; a conflict during review sets a "conflicted" flag.
- Applied and Rejected are final; revert and re-submission are new tickets.
- Edits to date, slot, room, lecture or type clear approvals.
- A cancel can be reverted, and an extra lecture can also be raised.
- Tickets expire at the earliest involved occurrence, with no notification.
- Append-only audit log; notification table per transition.

## 2026-11-06 Open questions answered

- The ERP will not help: no API, data seeded manually, no sync for now.
- Meeting link stays optional; multiple CRs kept; in-app only with no reminders; students see results only.
- Question 8 decided: standalone for version 1. The real timetable is arranged manually; webhooks or similar come later and ERP sync is ignored for now.

## 2026-11-06 Spec migration

- The original spec was split into `overview/`, `requirements/`, `flows/`, `data/`, `decisions/` and `integration/erp-ownership.md`, and archived at `_archive/original-spec.md`.
- Shared terms, roles, change types and the room entity moved to `shared/`.
- Spec open question Q4 (room "--") is decided: online.
- Shared courses are handled per batch from a manually seeded list. The conflict check and notifications cover every listed batch. This replaces the earlier "skipped in version 1" decision.

## 2026-11-08 Revert right

- An exchange or substitute can be reverted before the lecture starts by its author, the affected faculty or the HOD, not only the affected faculty.
