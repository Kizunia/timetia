"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { browserApi, unwrap } from "@/lib/api/client";
import type { components } from "@/lib/api/schema.gen";
import type { ROOM_TYPE_LABEL, SESSION_LABEL } from "@/lib/labels";

type Schemas = components["schemas"];

export type Version = Schemas["TimetableVersionResponse"];
export type Entry = Schemas["TimetableEntryResponse"];
export type NewVersion = Schemas["CreateVersionRequest"];
export type NewEntry = Schemas["CreateEntryRequest"];
export type User = Schemas["UserResponse"];

export type ResourceKey =
  | "departments"
  | "divisions"
  | "batches"
  | "subjects"
  | "rooms"
  | "faculties"
  | "academic-terms"
  | "teaching-assignments";

/** A row of any of the eight campus lists. Which of the optional fields it has depends on the list. */
export interface Row {
  id: string;
  version: number;
  code?: string;
  name?: string;
  departmentId?: string;
  divisionId?: string;
  batchId?: string | null;
  subjectId?: string;
  facultyId?: string;
  sessionType?: keyof typeof SESSION_LABEL;
  roomType?: keyof typeof ROOM_TYPE_LABEL;
  capacity?: number | null;
  userId?: string | null;
  startDate?: string;
  endDate?: string;
}

/**
 * The eight lists share one shape (api-reference.md section 7), so one set of calls serves them all. The path is typed as one
 * of them for the client's sake; the rows are read through {@link Row}.
 */
const pathOf = (key: ResourceKey) => `/api/v1/${key}` as "/api/v1/subjects";

const PAGE_SIZE = 100;

async function allRows(key: ResourceKey, filters: Record<string, string>): Promise<Row[]> {
  const rows: Row[] = [];
  for (let page = 0; ; page += 1) {
    const result = unwrap(await browserApi().GET(pathOf(key), { params: { query: { ...filters, page, size: PAGE_SIZE } } }));
    rows.push(...(result.items as unknown as Row[]));
    if (page + 1 >= result.page.totalPages) return rows;
  }
}

/** Every row of a list, narrowed by the filters the list takes. */
export function useRows(key: ResourceKey, filters: Record<string, string> = {}, enabled = true) {
  return useQuery({
    queryKey: ["setup", key, filters],
    queryFn: () => allRows(key, filters),
    placeholderData: keepPreviousData,
    enabled,
  });
}

/** What a change to the campus makes out of date: these lists, the names other screens show, and the audit log. */
function useRefresh() {
  const client = useQueryClient();
  return () => Promise.all(["setup", "directory", "me-campus", "live", "audit"].map((key) => client.invalidateQueries({ queryKey: [key] })));
}

export function useCreateRow(key: ResourceKey) {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: async (body: Record<string, unknown>) => unwrap(await browserApi().POST(pathOf(key), { body: body as never })),
    onSuccess: refresh,
  });
}

export function useRenameRow(key: ResourceKey) {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: async ({ id, name, version }: { id: string; name: string; version: number }) =>
      unwrap(
        await browserApi().PUT(`/api/v1/${key}/{id}` as "/api/v1/subjects/{id}", { params: { path: { id } }, body: { name, version } }),
      ),
    // Also after a refusal: a stale version means the list on screen is out of date.
    onSettled: refresh,
  });
}

export function useDeleteRow(key: ResourceKey) {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: async (id: string) =>
      unwrap(await browserApi().DELETE(`/api/v1/${key}/{id}` as "/api/v1/subjects/{id}", { params: { path: { id } } })),
    onSuccess: refresh,
  });
}

/** Accounts matching a text, to choose the one a faculty member signs in as. */
export function useAccounts(q: string) {
  return useQuery({
    queryKey: ["users", { setup: q }],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/users", { params: { query: { page: 0, size: 20, ...(q ? { q } : {}) } } })),
    placeholderData: keepPreviousData,
  });
}

export function useLinkAccount() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: async ({ id, userId, version }: { id: string; userId: string | null; version: number }) =>
      unwrap(
        await browserApi().PUT("/api/v1/faculties/{id}/account", {
          params: { path: { id } },
          // A null removes the link; the generated type only knows the field as optional.
          body: { userId: userId as string | undefined, version },
        }),
      ),
    onSettled: refresh,
  });
}

// --- timetables ---------------------------------------------------------------------------------------------------------

export function useVersions(params: { page: number; division?: string; status?: Version["status"] }) {
  return useQuery({
    queryKey: ["setup", "versions", params],
    queryFn: async () =>
      unwrap(await browserApi().GET("/api/v1/timetable/versions", { params: { query: { ...params, size: 20 } } })),
    placeholderData: keepPreviousData,
  });
}

export function useVersion(id: string) {
  return useQuery({
    queryKey: ["setup", "version", id],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/timetable/versions/{id}", { params: { path: { id } } })),
  });
}

export function useEntries(id: string) {
  return useQuery({
    queryKey: ["setup", "entries", id],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/timetable/versions/{id}/entries", { params: { path: { id } } })),
  });
}

export function useCreateVersion() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: async (body: NewVersion) => unwrap(await browserApi().POST("/api/v1/timetable/versions", { body })),
    onSuccess: refresh,
  });
}

export function useDeleteVersion() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: async (id: string) => unwrap(await browserApi().DELETE("/api/v1/timetable/versions/{id}", { params: { path: { id } } })),
    onSuccess: refresh,
  });
}

export function useAddEntry(versionId: string) {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: async (body: NewEntry) =>
      unwrap(await browserApi().POST("/api/v1/timetable/versions/{id}/entries", { params: { path: { id: versionId } }, body })),
    onSuccess: refresh,
  });
}

export function useDeleteEntry(versionId: string) {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: async (entryId: string) =>
      unwrap(
        await browserApi().DELETE("/api/v1/timetable/versions/{id}/entries/{entryId}", {
          params: { path: { id: versionId, entryId } },
        }),
      ),
    onSuccess: refresh,
  });
}

export function usePublishVersion(id: string) {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: async () => unwrap(await browserApi().POST("/api/v1/timetable/versions/{id}/publish", { params: { path: { id } } })),
    onSuccess: refresh,
  });
}
