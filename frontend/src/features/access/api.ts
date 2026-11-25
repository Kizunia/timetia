"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { browserApi, unwrap } from "@/lib/api/client";
import type { components } from "@/lib/api/schema.gen";

export type Role = components["schemas"]["RoleResponse"];
export type PermissionInfo = components["schemas"]["PermissionResponse"];

export function useRoles(enabled = true) {
  return useQuery({
    queryKey: ["roles"],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/roles", { params: { query: { size: 100, sort: "name,asc" } } })),
    enabled,
    placeholderData: keepPreviousData,
  });
}

/** One role. Without an ID, as when a new role is being created, nothing is requested. */
export function useRole(id: string | undefined) {
  return useQuery({
    queryKey: ["role", id],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/roles/{id}", { params: { path: { id: id! } } })),
    enabled: Boolean(id),
  });
}

/** Every permission the modules declare, for the role editor. */
export function usePermissionCatalogue() {
  return useQuery({
    queryKey: ["permissions"],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/permissions")),
    staleTime: 5 * 60_000,
  });
}

/** What changes when a role changes: the roles, who holds them, and the audit log. */
function useRefreshRoles() {
  const client = useQueryClient();
  return () =>
    Promise.all([
      client.invalidateQueries({ queryKey: ["roles"] }),
      client.invalidateQueries({ queryKey: ["role"] }),
      client.invalidateQueries({ queryKey: ["user-roles"] }),
      client.invalidateQueries({ queryKey: ["scoped-roles"] }),
      client.invalidateQueries({ queryKey: ["users"] }),
      client.invalidateQueries({ queryKey: ["user"] }),
      client.invalidateQueries({ queryKey: ["audit"] }),
    ]);
}

export interface RoleInput {
  name: string;
  description: string;
  permissions: string[];
}

export function useCreateRole() {
  const refresh = useRefreshRoles();
  return useMutation({
    mutationFn: async (body: RoleInput) => unwrap(await browserApi().POST("/api/v1/roles", { body })),
    onSuccess: refresh,
  });
}

export function useUpdateRole(id: string) {
  const refresh = useRefreshRoles();
  return useMutation({
    mutationFn: async (input: RoleInput & { version: number }) =>
      unwrap(await browserApi().PUT("/api/v1/roles/{id}", { params: { path: { id } }, body: input })),
    onSuccess: refresh,
  });
}

export function useDeleteRole(id: string) {
  const refresh = useRefreshRoles();
  return useMutation({
    mutationFn: async () => unwrap(await browserApi().DELETE("/api/v1/roles/{id}", { params: { path: { id } } })),
    onSuccess: refresh,
  });
}

export function useUserRoles(userId: string, enabled = true) {
  return useQuery({
    queryKey: ["user-roles", userId],
    queryFn: async () =>
      unwrap(await browserApi().GET("/api/v1/users/{userId}/roles", { params: { path: { userId } } })),
    enabled,
  });
}

export function useReplaceUserRoles(userId: string) {
  const refresh = useRefreshRoles();
  return useMutation({
    mutationFn: async (roleIds: string[]) =>
      unwrap(await browserApi().PUT("/api/v1/users/{userId}/roles", { params: { path: { userId } }, body: { roleIds } })),
    onSuccess: refresh,
  });
}

export type ScopedRole = components["schemas"]["ScopedRoleResponse"];

/** The roles one user holds within a department, division or batch (or everywhere), apart from the platform roles. */
export function useScopedRoles(userId: string, enabled = true) {
  return useQuery({
    queryKey: ["scoped-roles", userId],
    queryFn: async () =>
      unwrap(await browserApi().GET("/api/v1/users/{userId}/scoped-roles", { params: { path: { userId } } })),
    enabled,
  });
}

export interface ScopedRoleInput {
  roleId: string;
  /** The code of the department, division or batch; absent for a role that applies everywhere. */
  scopeCode?: string;
}

/**
 * Adds one role in one place and leaves the user's others alone, so a screen that was opened before another change
 * cannot undo it.
 */
export function useAddScopedRole(userId: string) {
  const refresh = useRefreshRoles();
  return useMutation({
    mutationFn: async (input: ScopedRoleInput) =>
      unwrap(await browserApi().POST("/api/v1/users/{userId}/scoped-roles", { params: { path: { userId } }, body: input })),
    onSuccess: refresh,
  });
}

/** Takes away one role in one place. */
export function useRemoveScopedRole(userId: string) {
  const refresh = useRefreshRoles();
  return useMutation({
    mutationFn: async (role: { roleId: string; scopeId?: string | null }) =>
      unwrap(
        await browserApi().DELETE("/api/v1/users/{userId}/scoped-roles/{roleId}", {
          params: { path: { userId, roleId: role.roleId }, query: role.scopeId ? { scopeId: role.scopeId } : {} },
        }),
      ),
    onSuccess: refresh,
  });
}
