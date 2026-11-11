"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { browserApi, unwrap } from "@/lib/api/client";
import type { components } from "@/lib/api/schema.gen";

export type User = components["schemas"]["UserResponse"];
export type UserState = User["state"];

export interface UserListParams {
  page: number;
  size: number;
  q?: string;
  state?: UserState;
  sort?: string;
}

/** What an administrator can do to one user. Each is a call of its own, so the screen shows only what applies. */
export type UserAction = "disable" | "enable" | "delete" | "restore" | "erase" | "resend-invitation";

export function useUsers(params: UserListParams) {
  return useQuery({
    queryKey: ["users", params],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/users", { params: { query: params } })),
    placeholderData: keepPreviousData,
  });
}

export function useUser(id: string) {
  return useQuery({
    queryKey: ["user", id],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/users/{id}", { params: { path: { id } } })),
  });
}

/** What changes when an administrator changes a user: the user, the lists, the audit log and the user's roles. */
function useRefreshUsers() {
  const client = useQueryClient();
  return () =>
    Promise.all([
      client.invalidateQueries({ queryKey: ["users"] }),
      client.invalidateQueries({ queryKey: ["user"] }),
      client.invalidateQueries({ queryKey: ["user-roles"] }),
      client.invalidateQueries({ queryKey: ["audit"] }),
    ]);
}

export function useInviteUser() {
  const refresh = useRefreshUsers();
  return useMutation({
    mutationFn: async (body: { email: string; roleIds: string[] }) =>
      unwrap(await browserApi().POST("/api/v1/users", { body })),
    onSuccess: refresh,
  });
}

export function useUserAction(id: string) {
  const refresh = useRefreshUsers();
  return useMutation({
    mutationFn: async (action: UserAction): Promise<void> => {
      const api = browserApi();
      const path = { params: { path: { id } } };
      switch (action) {
        case "disable":
          unwrap(await api.POST("/api/v1/users/{id}/disable", path));
          return;
        case "enable":
          unwrap(await api.POST("/api/v1/users/{id}/enable", path));
          return;
        case "restore":
          unwrap(await api.POST("/api/v1/users/{id}/restore", path));
          return;
        case "erase":
          unwrap(await api.POST("/api/v1/users/{id}/erase", path));
          return;
        case "resend-invitation":
          unwrap(await api.POST("/api/v1/users/{id}/invitation", path));
          return;
        case "delete":
          unwrap(await api.DELETE("/api/v1/users/{id}", path));
          return;
      }
    },
    onSuccess: refresh,
  });
}

/** What changes when the user's avatar does: their profile, which the server renders from, and what shows it. */
function useRefreshMe() {
  const client = useQueryClient();
  return () => client.invalidateQueries({ queryKey: ["me"] });
}

/** Uploads the file as the user's own avatar. The API checks the type from the bytes, so the browser's word is not relied on. */
export function useUploadAvatar() {
  const refresh = useRefreshMe();
  return useMutation({
    mutationFn: async (file: File) =>
      unwrap(
        await browserApi().PUT("/api/v1/me/avatar", {
          // The generated type of a file part is a string; the body is sent as multipart form data.
          body: { file: file as unknown as string },
          bodySerializer: () => {
            const form = new FormData();
            form.append("file", file);
            return form;
          },
        }),
      ),
    onSuccess: refresh,
  });
}

/** Removes the user's own avatar. */
export function useRemoveAvatar() {
  const refresh = useRefreshMe();
  return useMutation({
    mutationFn: async (): Promise<void> => {
      unwrap(await browserApi().DELETE("/api/v1/me/avatar"));
    },
    onSuccess: refresh,
  });
}

/** Changes the colour scheme on the user's own profile. */
export function useUpdateTheme() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (theme: "SYSTEM" | "LIGHT" | "DARK") =>
      unwrap(await browserApi().PUT("/api/v1/me/preferences", { body: { theme } })),
    onSuccess: () => client.invalidateQueries({ queryKey: ["me"] }),
  });
}
