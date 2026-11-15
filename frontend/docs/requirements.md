# Frontend requirements

Source: the teammate's brief for the frontend, kept in full (section 1), plus additions that make the result consistent
and easy for other agents to continue (section 2).

## 1. The brief (from the teammate)

- Use **shadcn/ui** for the UI. Use the shadcn skill (<https://ui.shadcn.com/docs/skills>); install with `pnpm dlx skills add shadcn/ui`.
- Use **Tailwind**, and try to use **only the Tailwind CSS variables** (semantic theme tokens).
- **No gradient colours anywhere.**
- The UI should be **basic and just work**.
- **Separate the UI by feature.**
- Use **stores, like zustand**.
- Use **zod**. Use **react-hook-form** if useful.
- Free to choose libraries.
- **Modular code, separated concerns**: separate the HTTP layer, the API layer, error handling, stores, libs, functions, components, and so on.
- The code must be **easy to upgrade and flexible/modular**.
- Write these requirements into the frontend's docs folder so other AI agents can refer to them (this folder).
- Add more things that make a good frontend where useful.
- **Focus on delivery and time.** Do not over-engineer what needs little work.
- The whole course project must be **completed within three phases** (see [plan.md](plan.md)). Do not polish the frontend.

## 2. Additions

### State
- **Server data** (anything the API returns) lives in TanStack Query, never in zustand.
- **zustand** holds UI state only: selected week, filters, selected lecture, open dialogs. One store per feature, in `store.ts`.

### Styling
- Only semantic tokens: `bg-background`, `bg-card`, `bg-muted`, `bg-primary`, `text-foreground`, `text-muted-foreground`, `text-destructive`, `border`, `ring`, and the like.
- Forbidden: colour literals (`#fff`, `rgb()`, `oklch()` in components), palette classes (`bg-red-500`), and gradients (`bg-gradient-*`, `bg-linear-*`, `from-*`, `via-*`, `to-*`, `linear-gradient(`).
- A colour the theme lacks (for example a "changed" badge) is added once as a variable in `src/app/globals.css`, for both light and dark, and used through a class. Prefer the existing `Badge` variants first.
- Existing exception: `src/app/shadcn.css` contains generated scroll-fade masks that use `linear-gradient` for masking, not colour. Leave it alone; do not add new ones.
- Light and dark must both work (theme comes from the user's preference via `ThemeSync`).

### API and errors
- Requests go through the typed client in `src/lib/api/client.ts` (`browserApi()` + `unwrap()`), only from a feature's `api.ts`. Never `fetch` directly, never hand-write request/response types: use `components["schemas"][...]` from `schema.gen.ts`.
- Errors are `ApiError` (`src/lib/api/problem.ts`). User-facing text is chosen from the problem `code` in the `MESSAGES` map there; add every new Timetia code (list in [backend-audit.md](backend-audit.md)). Show errors with `components/ErrorAlert.tsx`; put field errors on forms with `applyProblem` (`src/lib/forms.ts`).
- Show the `traceId` (ErrorAlert already does).

### Forms
- zod schema in the feature's `schemas.ts`; react-hook-form with `zodResolver`. Client validation is a convenience: the server is authoritative and its field errors are shown too. Pattern: `src/features/notes/NoteEditor.tsx`.
- Mutations that refresh data use `mutateAsync` then toast or navigate (see `kibootia-docs/architecture/frontend.md`, "Mutations").

### Permissions
- UI checks use `usePermissions()` / `<Can>` from `src/features/identity/permissions.tsx`. They only hide or disable; the backend enforces everything. Never rely on them for security.
- A 404 means "you may not see it", 403 "you see it but may not do it", 409 "state or conflict forbids it". Handle each with words, not a blank page.

### Every screen
- Has loading (Skeleton), empty ("nothing here yet" in plain words) and error (ErrorAlert) states.
- Works at 400 px width (stack or scroll tables, no horizontal page scroll).
- Keyboard reachable, labelled inputs, visible focus (shadcn gives most of it; do not remove it).
- English text written directly in components. No i18n library.
- Dates: store and send ISO strings (`YYYY-MM-DD`, `HH:mm`). Never build dates with `new Date("YYYY-MM-DD")` (UTC shift); use the helpers in `src/lib/dates.ts`. The campus time zone is the backend's (default Asia/Kolkata); the UI shows the dates the API gives and never converts them.

### Code
- A feature folder is self-contained; features import each other only in the direction given in [architecture.md](architecture.md).
- Pure logic (which buttons a ticket shows, week math, grid placement) is a plain function with a Vitest unit test. Components stay thin.
- No new dependency without a reason that fits in one line in the PR. Allowed additions: `zustand`, and the shadcn components `tabs` and `sheet` (they use the already-installed `radix-ui`). Everything else needed is already installed. In particular there is **no date-picker library**: dates use `<Input type="date">`, so the shadcn `calendar` component (which needs `react-day-picker` and `date-fns`) is not added.
- Keep files short (about 200 lines); split a screen into components when it grows.

### Definition of done for a screen
1. Works against the real backend as the right role (see `scripts/DEMO.md` for accounts).
2. Loading, empty, error states present.
3. `lint`, `typecheck`, `test` pass; build passes at the end of a phase.
4. Tick the box in [plan.md](plan.md).

## 3. Out of scope

Visual polish, animations, i18n, offline mode, push or email notifications, real-time sockets (polling is enough),
drag-and-drop timetable editing, charts. See [backend-audit.md](backend-audit.md) for features deferred on the backend.
