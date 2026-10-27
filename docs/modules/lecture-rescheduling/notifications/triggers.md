# Notification triggers

- **Type:** rules
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

Original design: [module spec](../../lecture-rescheduling.md), section 11. Where this file differs from the spec, this file wins.

- **Channel:** the first version uses the **in-app channel only**. Email, push, SMS and WhatsApp are not part of this version.
- **Who:** the author, requested reviewers, mentioned faculty, the HOD where relevant, the CR/BR within scope, and every affected student. Students of other divisions in shared courses are not covered yet ([shared courses](../rules/shared-courses.md)).
- **Events:** ticket raised, review requested, approved, rejected, expired, applied, reverted, and "an issue mentions you".
- **No reminder** is sent before a moved or extra lecture. Only the change notification is sent.
- A moved, cancelled or added occurrence still emits an event for the ERP attendance module.
