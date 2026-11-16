"use client";

import { useQuery } from "@tanstack/react-query";

import { browserApi, unwrap } from "@/lib/api/client";
import type { components } from "@/lib/api/schema.gen";

type Schemas = components["schemas"];

export type Department = Schemas["DirectoryDepartmentResponse"];
export type Division = Schemas["DirectoryDivisionResponse"];
export type Batch = Schemas["DirectoryBatchResponse"];
export type Room = Schemas["DirectoryRoomResponse"];
export type Faculty = Schemas["DirectoryFacultyResponse"];
export type MyCampus = Schemas["MyCampusResponse"];
export type ScopedRole = Schemas["ScopedRoleResponse"];

/** Campus names change rarely, so they are kept for five minutes. */
const DIRECTORY_STALE_MS = 5 * 60_000;

export function useDepartments() {
  return useQuery({
    queryKey: ["directory", "departments", null],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/directory/departments")),
    staleTime: DIRECTORY_STALE_MS,
  });
}

export function useDivisions() {
  return useQuery({
    queryKey: ["directory", "divisions", null],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/directory/divisions")),
    staleTime: DIRECTORY_STALE_MS,
  });
}

/** The batches of one division, or all of them without an ID. */
export function useBatches(divisionId?: string | null) {
  return useQuery({
    queryKey: ["directory", "batches", { division: divisionId ?? null }],
    queryFn: async () =>
      unwrap(
        await browserApi().GET("/api/v1/directory/batches", {
          params: { query: divisionId ? { division: divisionId } : {} },
        }),
      ),
    staleTime: DIRECTORY_STALE_MS,
  });
}

export function useRooms() {
  return useQuery({
    queryKey: ["directory", "rooms", null],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/directory/rooms")),
    staleTime: DIRECTORY_STALE_MS,
  });
}

/** The faculty of one department, or all of them without an ID. */
export function useFaculties(departmentId?: string | null) {
  return useQuery({
    queryKey: ["directory", "faculties", { department: departmentId ?? null }],
    queryFn: async () =>
      unwrap(
        await browserApi().GET("/api/v1/directory/faculties", {
          params: { query: departmentId ? { department: departmentId } : {} },
        }),
      ),
    staleTime: DIRECTORY_STALE_MS,
  });
}

/** The faculty records of the signed-in person and what they teach. */
export function useMyCampus() {
  return useQuery({
    queryKey: ["me-campus"],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/directory/me")),
    staleTime: DIRECTORY_STALE_MS,
  });
}

/** The roles the signed-in person holds in places. */
export function useMyScopedRoles() {
  return useQuery({
    queryKey: ["my-scoped-roles"],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/me/scoped-roles")),
    staleTime: DIRECTORY_STALE_MS,
  });
}
