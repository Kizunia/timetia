# Handover: Phase 2 (tickets)

Branch `dog`. Written 2026-11-16. Spec: `frontend/docs/phase-2.md` (checklist at its end is the honest status).

## Done
- **Backend**: names on ticket responses (`TicketNameService`), `GET /tickets/{id}/reviewer-candidates` (`ReviewerCandidateService`,
  `ScopeHolders.usersHolding`, new `rescheduling -> access` edge pinned in `ModularityTest`), `awaitingReview` list filter
  (`TicketQuery.awaitingAnswerOf`, an extra condition on top of normal visibility), `GET /timetable/free-rooms`
  (`RoomAvailabilityService`, one batched claims query `ClaimStore.holdersOf`), `AcademicDirectory.roomOptions()`.
- **issues** persistence moved from JPA to hand-written SQL (`JdbcIssueStore`).
- **Frontend**: `features/tickets` (list, detail, new extra lecture, raise/edit/review/submit/escalate/revert dialogs), `/tickets` routes,
  registry and proxy entries, `LectureActions` in the lecture sheet via `app/timetable/TimetableWithTickets.tsx`.
- **Docs**: `timetia-docs/engineering/database.md` (schema, queries, viva questions) written by a parallel agent.

## Run
- Backend ITs: 118 integration + 14 unit tests green (new ITs, all `rescheduling.*` ITs, `IssueApiIT`, `OpenApiContractIT`,
  `ArchitectureTest`, `ModularityTest`). Frontend: typecheck, lint, 302 unit tests (31 files), `next build` green.

## NOT done (be honest about these)
- Full backend `mvnw verify` (the first attempt was spoiled by editing sources while it ran).
- Browser exit checks of Phase 1 and Phase 2 (no Playwright specs written or run).
- Component tests for the tickets screens and dialogs (`F2.8` items 2-6).
- Docs: changelog line, `scripts/DEMO.md` intro.

## Notes
- Run vitest from PowerShell. From Git Bash on this machine every jest-dom matcher fails in multi-file runs (path-case issue); it is not a code problem.
- `awaitingReview` deviates from the spec on purpose: it narrows the normal visibility instead of replacing it, so a pure HOD sees their department's tickets.
