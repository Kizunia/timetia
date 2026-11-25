"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { browserApi, unwrap } from "@/lib/api/client";
import type { components } from "@/lib/api/schema.gen";

type Schemas = components["schemas"];

export type Ticket = Schemas["TicketResponse"];
export type TicketChange = Ticket["change"];
export type Review = Ticket["reviews"][number];
export type ReviewerCandidate = Schemas["ReviewerCandidateResponse"];
export type FreeRoom = Schemas["FreeRoomResponse"];

export interface TicketListParams {
  page: number;
  size: number;
  status?: Ticket["status"];
  mine?: boolean;
  awaitingReview?: boolean;
  lecture?: string;
  sort?: string;
}

/** One page of tickets. A list carries no reviews or conflicts, only whether there are conflicts. */
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

/** The people the author may ask to review, in the capacity to send with the request. Only the author may ask. */
export function useReviewerCandidates(ticketId: string, enabled: boolean) {
  return useQuery({
    queryKey: ["candidates", ticketId],
    queryFn: async () =>
      unwrap(await browserApi().GET("/api/v1/tickets/{id}/reviewer-candidates", { params: { path: { id: ticketId } } })),
    enabled,
  });
}

export interface FreeRoomParams {
  date: string;
  startTime: string;
  endTime: string;
  sessionType?: Schemas["LiveLectureResponse"]["sessionType"];
  excludeLecture?: string;
}

/** The rooms free at a time. Without parameters (a form not filled in far enough) nothing is requested. */
export function useFreeRooms(params: FreeRoomParams | null) {
  return useQuery({
    queryKey: ["free-rooms", params],
    queryFn: async () =>
      unwrap(await browserApi().GET("/api/v1/timetable/free-rooms", { params: { query: params! } })),
    enabled: params !== null,
    staleTime: 10_000,
  });
}

/** Everything a ticket action can change: lists, the ticket, the live timetable, free rooms, notifications, the audit log. */
function useRefreshTickets() {
  const client = useQueryClient();
  return () =>
    Promise.all(
      ["tickets", "ticket", "live", "lecture", "free-rooms", "notifications", "unread", "audit"].map((key) =>
        client.invalidateQueries({ queryKey: [key] }),
      ),
    );
}

export function useCreateTicket() {
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: async (body: Schemas["CreateTicketRequest"]) =>
      unwrap(await browserApi().POST("/api/v1/tickets", { body })),
    onSuccess: refresh,
  });
}

export function useEditTicket(id: string) {
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: async (body: Schemas["UpdateTicketRequest"]) =>
      unwrap(await browserApi().PUT("/api/v1/tickets/{id}", { params: { path: { id } }, body })),
    onSuccess: refresh,
  });
}

export function useApplyTicket(id: string) {
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: async () => unwrap(await browserApi().POST("/api/v1/tickets/{id}/apply", { params: { path: { id } } })),
    // A refused apply still changes the ticket (its conflicts are stored), so the page refreshes either way.
    onSettled: refresh,
  });
}

export function useSubmitTicket(id: string) {
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: async (body: Schemas["SubmitTicketRequest"]) =>
      unwrap(await browserApi().POST("/api/v1/tickets/{id}/submit", { params: { path: { id } }, body })),
    onSuccess: refresh,
  });
}

export function useReviewTicket(id: string) {
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: async (body: Schemas["ReviewRequest"]) =>
      unwrap(await browserApi().POST("/api/v1/tickets/{id}/review", { params: { path: { id } }, body })),
    onSuccess: refresh,
  });
}

export function useEscalateTicket(id: string) {
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: async (body: Schemas["EscalateRequest"]) =>
      unwrap(await browserApi().POST("/api/v1/tickets/{id}/escalate", { params: { path: { id } }, body })),
    onSuccess: refresh,
  });
}

export function useWithdrawTicket(id: string) {
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: async () => unwrap(await browserApi().POST("/api/v1/tickets/{id}/withdraw", { params: { path: { id } } })),
    onSuccess: refresh,
  });
}

/** Raises the revert of an applied ticket as a new draft ticket. */
export function useRevertTicket(id: string) {
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: async (body: Schemas["RevertRequest"]) =>
      unwrap(await browserApi().POST("/api/v1/tickets/{id}/revert", { params: { path: { id } }, body })),
    onSuccess: refresh,
  });
}
