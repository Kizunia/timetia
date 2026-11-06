# Room

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-06

Details on how rooms are used in the module: [rooms and location](../../modules/lecture-rescheduling/rules/rooms-and-location.md).

| Attribute | Example |
| --- | --- |
| Building | A, B, C, D, E |
| Floor | 1 |
| Room number | 05 |
| Code (display) | B105 |
| Type | Classroom, Tutorial room, Lab (with lab kind) |
| Seats | 70 |
| Projector | Yes / No |
| Owning department | CSE |

Building, floor and number are stored as separate fields rather than parsed from the code, since parsing breaks on ground floors, basements and room numbers above 99.
