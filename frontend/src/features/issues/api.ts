"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { browserApi, unwrap } from "@/lib/api/client";
import type { components } from "@/lib/api/schema.gen";

export type Issue = components["schemas"]["IssueResponse"];
export type NewIssue = components["schemas"]["CreateIssueRequest"];

export function useIssues(params: { page: number; size: number; status?: Issue["status"]; mine?: boolean }) {
  return useQuery({
    queryKey: ["issues", params],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/issues", { params: { query: params } })),
    placeholderData: keepPreviousData,
  });
}

export function useIssue(id: string) {
  return useQuery({
    queryKey: ["issue", id],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/issues/{id}", { params: { path: { id } } })),
  });
}

function useRefresh() {
  const client = useQueryClient();
  return () =>
    Promise.all(["issues", "issue", "notifications", "unread", "audit"].map((key) => client.invalidateQueries({ queryKey: [key] })));
}

export function useCreateIssue() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: async (body: NewIssue) => unwrap(await browserApi().POST("/api/v1/issues", { body })),
    onSuccess: refresh,
  });
}

export function useCloseIssue(id: string) {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: async () => unwrap(await browserApi().POST("/api/v1/issues/{id}/close", { params: { path: { id } } })),
    onSuccess: refresh,
  });
}
