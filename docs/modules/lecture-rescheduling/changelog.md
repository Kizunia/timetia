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
