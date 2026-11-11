"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { browserApi, unwrap } from "@/lib/api/client";
import type { components } from "@/lib/api/schema.gen";

export type AuditEntry = components["schemas"]["AuditEntryResponse"];
export type Person = components["schemas"]["PersonResponse"];

export interface AuditParams {
  page: number;
  size: number;
  action?: string;
  targetType?: string;
}

export function useAuditEntries(params: AuditParams) {
  return useQuery({
    queryKey: ["audit", params],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/audit-entries", { params: { query: params } })),
    placeholderData: keepPreviousData,
  });
}
