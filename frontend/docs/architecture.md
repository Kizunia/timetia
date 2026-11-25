# Frontend architecture

The frontend is the existing Next.js 16 app in `frontend/` (App Router, React 19, TypeScript 5.9, Tailwind 4, shadcn
`radix-nova`, TanStack Query 5, react-hook-form 7 + zod 4, `openapi-fetch`). Timetia screens are added as **feature
folders** beside the existing ones (`identity`, `access`, `audit`, `notes`). Platform conventions are in
`kibootia-docs/architecture/frontend.md`; this file is the working detail.

## 1. Request path (do not change)

```
Browser component ──(cookie + x-timetia-request: 1)──► Next.js /api/[...path] proxy ──(Bearer token)──► Spring /api/v1/*
Server component  ──────────────(Bearer token from session cookie)────────────────────────────────────► Spring
```

Client components use `browserApi()` (`src/lib/api/client.ts`); server components use `serverApi()` (only `getMe` in
`src/lib/server-me.ts` does, to load the user once per request). **All Timetia screens are client components fed by
TanStack Query**; the only server work is `PageShell` loading `me`.

## 2. Layers and folders

```
src/
  app/<route>/page.tsx        ROUTES ONLY: `<PageShell width="wide"><XScreen/></PageShell>`, `export const dynamic = "force-dynamic"`.
  components/ui/              shadcn components, added only with the shadcn CLI. Do not hand-write.
  components/                 shared app components used by 2+ features (PageShell, SiteHeader, ErrorAlert, Pager, + NEW below)
  lib/api/client.ts           HTTP layer. browserApi(), unwrap(). Do not change.
  lib/api/problem.ts          Error layer. ApiError, MESSAGES (add codes here), messageFor, fieldErrors.
  lib/api/schema.gen.ts       GENERATED types. Never edit.
  lib/forms.ts                applyProblem(): server field errors onto a react-hook-form.
  lib/dates.ts                NEW (P1): pure ISO date/time helpers.
  lib/labels.ts               NEW (P1): the label maps of domain.md section 6.
  lib/query.tsx               QueryClient defaults (no retry on 4xx, staleTime 15 s).
  features/<feature>/         one folder per feature (section 3).
  modules.ts                  navigation + protected-path registry.
tests/unit/*.test.ts(x)       Vitest. tests/e2e/*.spec.ts  Playwright.
```

Dependency direction (an arrow means "may import from"):

```
app/**/page.tsx → features/*/…Screen → features/*/{api,store,schemas,lib,components} → lib/*, components/*
features:   issues → tickets → timetable → campus ;  notifications → campus ;  setup → campus
```
A lower feature never imports a higher one. Where two features must meet (the lecture sheet offering "Raise a ticket"),
the **higher** feature is passed in as a prop or a render slot from the page or screen above, or the lower feature
exports a slot. Concretely: `timetable` exports `<LectureSheet actions={...}>`; `tickets` supplies the actions;
`app/timetable/page.tsx` wires them. `identity/permissions.tsx` and `components/*` are importable by everything.

## 3. Inside a feature

```
features/tickets/
  api.ts            TanStack Query hooks. The ONLY file in the feature that imports browserApi/unwrap.
  schemas.ts        zod schemas for forms (+ the Values types).
  store.ts          zustand store: UI state only.
  lib/*.ts          pure functions (no React, no fetch) + tests in tests/unit/.
  components/*.tsx  presentational components: props in, JSX out; no data fetching.
  XScreen.tsx       container: calls hooks, reads the store, composes components. "use client".
```
Keep each file under about 200 lines; split when it grows. Names: hooks `useX`, screens `XScreen`, components
PascalCase files, pure modules camelCase files.

### 3.1 `api.ts` template (copy `src/features/notes/api.ts`)

```ts
"use client";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { browserApi, unwrap } from "@/lib/api/client";
import type { components } from "@/lib/api/schema.gen";

export type Ticket = components["schemas"]["TicketResponse"];

export interface TicketListParams { page: number; size: number; status?: Ticket["status"]; mine?: boolean; lecture?: string; sort?: string }

export function useTickets(params: TicketListParams) {
  return useQuery({
    queryKey: ["tickets", params],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/tickets", { params: { query: params } })),
    placeholderData: keepPreviousData,
  });
}
export function useTicket(id: string | undefined) {
  return useQuery({
    queryKey: ["ticket", id],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/tickets/{id}", { params: { path: { id: id! } } })),
    enabled: Boolean(id),
  });
}
/** Everything a ticket action can change: lists, the ticket, the live timetable, notifications, the audit log. */
function useRefreshTickets() {
  const client = useQueryClient();
  return () => Promise.all(["tickets", "ticket", "live", "notifications", "audit"].map((key) => client.invalidateQueries({ queryKey: [key] })));
}
export function useApplyTicket(id: string) {
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: async () => unwrap(await browserApi().POST("/api/v1/tickets/{id}/apply", { params: { path: { id } } })),
    onSuccess: refresh,
  });
}
```
Rules: one hook per call; `unwrap` always (it throws `ApiError`); invalidate by the shared query-key prefixes in
section 6; mutations are `mutateAsync`'d by the caller inside try/catch (the notes `NoteEditor` pattern).

For a **paged list that must be read completely** (directory-style data), write a `useAll...` hook that loops pages:
`while (page.number + 1 < page.totalPages)` with `size: 100`. For the **NEW unpaged** directory endpoints just call once.

### 3.2 `store.ts` template (zustand)

```ts
import { create } from "zustand";
import { startOfWeek, todayIso } from "@/lib/dates";

interface TimetableUi {
  weekStart: string;                  // ISO Monday
  divisionId: string | null;
  batchId: string | null;
  facultyId: string | null;
  selectedLectureId: string | null;
  setWeekStart: (iso: string) => void;
  setFilters: (f: Partial<Pick<TimetableUi, "divisionId" | "batchId" | "facultyId">>) => void;
  select: (id: string | null) => void;
  reset: () => void;
}
const initial = () => ({ weekStart: startOfWeek(todayIso()), divisionId: null, batchId: null, facultyId: null, selectedLectureId: null });
export const useTimetableUi = create<TimetableUi>((set) => ({
  ...initial(),
  setWeekStart: (weekStart) => set({ weekStart }),
  setFilters: (f) => set(f),
  select: (selectedLectureId) => set({ selectedLectureId }),
  reset: () => set(initial()),
}));
```
Rules: no API data in a store (ids and primitives only); stores are module-level singletons, so a test calls
`useTimetableUi.setState(initial())` in `beforeEach`; never read a store in a server component; the screen reads the
store with selectors (`useTimetableUi((s) => s.weekStart)`) to limit re-renders.

### 3.3 `schemas.ts` and forms (copy `NoteEditor.tsx`)

```ts
import { z } from "zod";
export const slotSchema = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Choose a date"),
  startHour: z.number().int().min(8).max(19),
  endHour: z.number().int().min(9).max(20),
  mode: z.enum(["IN_PERSON", "ONLINE"]),
  roomId: z.string().optional(),
  meetingLink: z.string().max(500).optional(),
}).refine((s) => s.endHour > s.startHour, { path: ["endHour"], message: "End must be after start" })
  .refine((s) => s.mode !== "IN_PERSON" || !!s.roomId, { path: ["roomId"], message: "Choose a room" });
```
Form values use hour integers; the API wants `"09:00"`: convert in one function `toTarget(values)` in `lib/slot.ts`
(`${String(h).padStart(2,"0")}:00`). Server nested field names map to form fields with a small table passed to
`applyProblem` as `fields`: the form registers `roomId`, so map `target.roomId` → `roomId`, `target.endTime` → `endHour`,
`target.date` → `date`, `target.meetingLink` → `meetingLink` **before** calling it:

```ts
const FIELD_MAP: Record<string, string> = { "target.roomId": "roomId", "target.endTime": "endHour", "target.startTime": "startHour",
  "target.date": "date", "target.meetingLink": "meetingLink", "target.mode": "mode" };
function remap(error: unknown) { // returns an ApiError whose errors[].field are the form's names
  if (!(error instanceof ApiError) || !error.problem?.errors) return error;
  return new ApiError(error.status, { ...error.problem, errors: error.problem.errors.map((e) => ({ ...e, field: FIELD_MAP[e.field] ?? e.field })) });
}
```
Put `remap` in `features/tickets/lib/problems.ts`. Everything `applyProblem` does not place returns as a string: show it in
an `<ErrorAlert message=...>` at the top of the form. A 409 `rescheduling.blocked_by_conflicts` is **not** a field error:
render `error.problem.errors` with `<ConflictList>` (the codes are conflict types in lower case).

### 3.4 Screen and page templates

```tsx
// src/features/tickets/TicketsScreen.tsx
"use client";
export function TicketsScreen() { /* hooks + store + <Tabs> + <TicketTable/> + <Pager/> */ }

// src/app/tickets/page.tsx
import { PageShell } from "@/components/PageShell";
import { TicketsScreen } from "@/features/tickets/TicketsScreen";
export const dynamic = "force-dynamic";
export default function TicketsPage() { return (<PageShell width="wide"><TicketsScreen /></PageShell>); }

// src/app/tickets/[id]/page.tsx  (Next 16: params is a Promise)
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (<PageShell width="wide"><TicketDetailScreen id={id} /></PageShell>);
}
```
Copy `src/app/notes/[id]/page.tsx` for the exact dynamic-route form used in this repo.

Every screen: `<section aria-labelledby="x-heading">` with one `<h1 id="x-heading">`; loading → `<Skeleton>` with
`aria-busy`; error → `<ErrorAlert error={query.error} title="…could not be loaded">`; empty → a muted sentence in the
table or card; paged → `<Pager>`.

## 4. Registering a feature (three places, one commit)

1. `src/modules.ts`: add a block between `// module:<id>:start` and `// module:<id>:end`:
   ```ts
   // module:timetable:start
   {
     id: "timetable",
     nav: { href: "/timetable", label: "Timetable", permissions: ["timetable.live.view", "timetable.live.view-own", "timetable.live.view-all"] },
     protectedPrefixes: ["/timetable"],
   },
   // module:timetable:end
   ```
   Today `nav` has one `permission: string` (the notes block uses it). Phase 1 changes it to
   `permissions: readonly string[]` (the link shows when the user holds **any**), and updates `moduleLinks`, the notes
   block and `tests/unit/modules.test.ts` (spec in phase-1.md, task F2).
2. `src/proxy.ts`: add `"/timetable", "/timetable/:path*", // module:timetable` to `config.matcher` (it must be a literal).
3. The page files under `src/app/<route>/`.

The sign-in redirect for these paths comes from `isModulePath` (`modules.ts`) used by `proxy.ts`.

## 5. Navigation and the shell

`SiteHeader` shows `Profile`, the module links from `moduleLinks(me.permissions)`, and `Administration` when
`visibleAdminSections(me)` is non-empty. Phase 3 adds the notifications bell to this header (client component
`NotificationBell`) and Phase 3 adds two sections to `ADMIN_SECTIONS` (`Campus`, `Timetables`). `AdminFrame` renders the
section list; admin routes live under `src/app/admin/<section>/page.tsx` and inherit `AdminFrame` from
`src/app/admin/layout.tsx`.

## 6. Cross-cutting conventions

**Query keys** (prefix = invalidation unit): `["me-campus"]`, `["directory", kind, params]`, `["live", params]`,
`["lecture", id]`, `["tickets", params]`, `["ticket", id]`, `["candidates", ticketId]`, `["free-rooms", params]`,
`["issues", params]`, `["issue", id]`, `["notifications", params]`, `["unread"]`, `["setup", resource, params]`,
`["versions", params]`, `["entries", versionId]`, plus the existing `["audit"]`, `["notes"]`...

**Invalidation matrix** (a mutation invalidates all in its row):

| Mutation | Invalidate |
|---|---|
| any ticket action | `tickets`, `ticket`, `live`, `lecture`, `notifications`, `unread`, `audit` |
| issue open/close/mention/link | `issues`, `issue`, `notifications`, `unread`, `audit` |
| read / read-all | `notifications`, `unread` |
| setup create/rename/delete | `["setup", resource]`, `["directory"]`, `["me-campus"]`, `audit` |
| publish | `versions`, `entries`, `live`, `audit` |

**Polling**: only `useUnreadCount` (`refetchInterval: 30_000`, off when the tab is hidden: `refetchIntervalInBackground: false`).

**Dates**: see `src/lib/dates.ts` (spec in phase-1.md). Never `new Date("YYYY-MM-DD")`, never `toISOString()` on a local
date, never convert instants to the campus zone in code: lecture dates and times are already campus-local; instants
(`createdAt`) are shown with `toLocaleString()` in the browser's zone.

**IDs → names**: until the NEW name fields exist use `useDirectory()` lookups; after, prefer response names. A null or
unknown name renders as an em dash with `title="Not found (it may have been deleted)"`; never crash.

**Permissions in UI**: `const { me, has, hasAny } = usePermissions()`; hide with `<Can permission="...">` or `has(...)`;
disable-with-reason when the user obviously should have the button but cannot (for example Apply while a conflict stands:
disabled, with the conflict list visible beside it).

**Toasts** (`sonner`): `toast.success("Ticket applied")` after a successful mutation; failures render an `ErrorAlert` near the
action (not a toast), so the reason stays on screen.

**Accessibility**: every input has a `<Label htmlFor>`; every icon-only button has `aria-label`; lists of cards use `<ul>`;
focus returns to the trigger after a dialog closes (shadcn does this); do not remove focus rings. The week grid is a real
`<table>` with `<th scope>` headers on desktop and a `<ul>` per day on narrow screens.

**Styling**: [requirements.md](requirements.md) section 2. Status colours come only from `Badge` variants and
`Button` variants (see `components/ui/badge.tsx`); lecture state uses text + variant, never colour alone:
`cancelled` → destructive badge "Cancelled" and a line-through title; `changed` → secondary badge "Moved" or "Changed";
`extra` → outline badge "Extra"; `substituted` → outline badge "Substitute"; `online` → outline badge "Online".

## 7. Testing

- **Unit (Vitest)** in `tests/unit/`. Pure logic: plain `describe/it`. Components: first line
  `// @vitest-environment jsdom`; render with `renderScreen(<X/>, meWith([...permissions]))` from `tests/unit/ui.tsx`;
  fake the API with `stubApi((call) => json(...))` (it records `calls` with `method`, `path`, `query`, `body`); mock
  `sonner` as `notes-screen.test.tsx` does. Reference: `tests/unit/notes-screen.test.tsx`, `note-editor.test.tsx`.
- Test the **contract the screen has with the API**: the request it sends (path, query, body) and what it shows for 200,
  empty, 403/404/409 problem answers (`problem(409, "rescheduling.blocked_by_conflicts", { errors: [...] })`).
- **E2E (Playwright)** in `tests/e2e/`, written in Phase 3 only, following `notes.spec.ts` + `support.ts` (`signIn`,
  `expectNoAccessibilityViolations`).
- Gates after each task: `pnpm --filter kibootia-frontend typecheck && … lint && … test` must show no failure beyond the
  15 baseline ones listed in README.

## 8. Directory of files this plan adds (frontend)

```
src/lib/dates.ts  src/lib/labels.ts
src/features/campus/{api.ts,useMyCampus.ts,lib/names.ts}                                           (P1)
src/features/timetable/{api.ts,store.ts,TimetableScreen.tsx,lib/{grid.ts,describe.ts},
                        components/{WeekGrid,LectureCard,LectureSheet,Filters}.tsx}                (P1)
src/features/tickets/{api.ts,schemas.ts,store.ts,TicketsScreen.tsx,TicketDetailScreen.tsx,NewTicketScreen.tsx,
                      lib/{actions,slot,problems}.ts,
                      components/{TicketTable,StatusBadge,ChangeSummary,ConflictList,ReviewList,ActionBar,
                                  RaiseDialog,SlotFields,ReviewerPicker,ReviewDialog,RevertDialog}.tsx}   (P2)
src/features/notifications/{api.ts,NotificationsScreen.tsx,components/{NotificationBell,NotificationRow}.tsx,lib/describe.ts}  (P3)
src/features/issues/{api.ts,schemas.ts,store.ts,IssuesScreen.tsx,IssueDetailScreen.tsx,components/*}                           (P3)
src/features/setup/{api.ts,resources.ts,SetupScreen.tsx,TimetablesScreen.tsx,components/*}                                    (P3)
src/app/{timetable,tickets,tickets/new,tickets/[id],notifications,issues,issues/[id],admin/campus,admin/timetables}/page.tsx
```
