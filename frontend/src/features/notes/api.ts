"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { browserApi, unwrap } from "@/lib/api/client";
import type { components } from "@/lib/api/schema.gen";

export type Note = components["schemas"]["NoteResponse"];

/** The named filters of the list (ADR 0043). `owner` narrows to one user's notes; `deleted` is the administrative view. */
export interface NoteListParams {
  page: number;
  size: number;
  q?: string;
  owner?: string;
  deleted?: boolean;
  sort?: string;
}

export function useNotes(params: NoteListParams) {
  return useQuery({
    queryKey: ["notes", params],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/notes", { params: { query: params } })),
    placeholderData: keepPreviousData,
  });
}

/** One note. Without an ID, as when a new note is being written, nothing is requested. */
export function useNote(id: string | undefined) {
  return useQuery({
    queryKey: ["note", id],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/notes/{id}", { params: { path: { id: id! } } })),
    enabled: Boolean(id),
  });
}

/** What changes when a note changes: the lists, the note itself, and the audit log. */
function useRefreshNotes() {
  const client = useQueryClient();
  return () =>
    Promise.all([
      client.invalidateQueries({ queryKey: ["notes"] }),
      client.invalidateQueries({ queryKey: ["note"] }),
      client.invalidateQueries({ queryKey: ["audit"] }),
    ]);
}

export interface NoteInput {
  title: string;
  body: string;
}

export function useCreateNote() {
  const refresh = useRefreshNotes();
  return useMutation({
    mutationFn: async (body: NoteInput) => unwrap(await browserApi().POST("/api/v1/notes", { body })),
    onSuccess: refresh,
  });
}

export function useUpdateNote(id: string) {
  const refresh = useRefreshNotes();
  return useMutation({
    mutationFn: async (input: NoteInput & { version: number }) =>
      unwrap(await browserApi().PUT("/api/v1/notes/{id}", { params: { path: { id } }, body: input })),
    onSuccess: refresh,
  });
}

export function useDeleteNote(id: string) {
  const refresh = useRefreshNotes();
  return useMutation({
    mutationFn: async (): Promise<void> => {
      unwrap(await browserApi().DELETE("/api/v1/notes/{id}", { params: { path: { id } } }));
    },
    onSuccess: refresh,
  });
}

/** Brings a deleted note back. Only for someone who may restore notes. */
export function useRestoreNote() {
  const refresh = useRefreshNotes();
  return useMutation({
    mutationFn: async (id: string) => unwrap(await browserApi().POST("/api/v1/notes/{id}/restore", { params: { path: { id } } })),
    onSuccess: refresh,
  });
}

/** Attaches a picture to a note, or replaces the one it has. The note's version does not change. */
export function useUploadAttachment(id: string) {
  const refresh = useRefreshNotes();
  return useMutation({
    mutationFn: async (file: File) =>
      unwrap(
        await browserApi().PUT("/api/v1/notes/{id}/attachment", {
          params: { path: { id } },
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

export function useRemoveAttachment(id: string) {
  const refresh = useRefreshNotes();
  return useMutation({
    mutationFn: async (): Promise<void> => {
      unwrap(await browserApi().DELETE("/api/v1/notes/{id}/attachment", { params: { path: { id } } }));
    },
    onSuccess: refresh,
  });
}

/**
 * Where a note's picture is served from: this application's own proxy, never a storage address. The version is the
 * cache-buster, so a replaced picture shows at once while an unchanged one stays in the browser's cache.
 */
export function attachmentUrl(noteId: string, size: "preview" | "full", version: string): string {
  return `/api/v1/notes/${noteId}/attachment?size=${size.toUpperCase()}&v=${version}`;
}
