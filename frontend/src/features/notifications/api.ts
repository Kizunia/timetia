"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { browserApi, unwrap } from "@/lib/api/client";
import type { components } from "@/lib/api/schema.gen";

export type Notification = components["schemas"]["NotificationResponse"];

/** The number of unread notifications. It is the only thing polled, every 30 seconds and not while the tab is hidden. */
export function useUnreadCount() {
  return useQuery({
    queryKey: ["unread"],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/notifications/unread-count")).count,
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
  });
}

export function useNotifications(params: { page: number; size: number; unread?: boolean }) {
  return useQuery({
    queryKey: ["notifications", params],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/notifications", { params: { query: params } })),
    placeholderData: keepPreviousData,
  });
}

function useRefresh() {
  const client = useQueryClient();
  return () => Promise.all(["notifications", "unread"].map((key) => client.invalidateQueries({ queryKey: [key] })));
}

export function useMarkRead() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: async (id: string) => unwrap(await browserApi().POST("/api/v1/notifications/{id}/read", { params: { path: { id } } })),
    onSuccess: refresh,
  });
}

export function useMarkAllRead() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: async () => unwrap(await browserApi().POST("/api/v1/notifications/read-all")),
    onSuccess: refresh,
  });
}
