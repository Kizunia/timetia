# Rooms and lecture location

- **Type:** rules
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

Original design: [module spec](../../lecture-rescheduling.md), sections 8.1 and 12. Where this file differs from the spec, this file wins.

- The **author picks the room**. The system only **suggests** free rooms that fit (room type, seats, projector).
- Every lecture has a [mode](../../../shared/glossary/lecture-mode.md): **in person** or **online**.
- An **in-person** lecture needs a room, so every changed in-person lecture has a place. This applies even when the original lecture had no room.
- An **online** lecture has **no room**, and the room checks are skipped for it. FCTC is an example: it runs fully online.
- Every lecture has one **location** field. For an in-person lecture it holds the **room number**; for an online lecture it holds the **meeting link** (mostly Google Meet).
- The meeting link is **optional for now**. If it is missing, students are told "link to follow". The owner may make it required later.
- The ERP's room "--" is read as **online**.
- For in-person lectures the room checks block as described in [conflict rules](conflict-rules.md). A missing projector is plain information only.
