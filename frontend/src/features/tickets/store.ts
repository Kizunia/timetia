import { create } from "zustand";

import type { Ticket } from "@/features/tickets/api";

export type TicketsTab = "mine" | "review" | "all";

interface TicketsUi {
  /** The tab the person chose; null until they do, and then the screen picks one for their role. */
  tab: TicketsTab | null;
  status: Ticket["status"] | null;
  page: number;
  setTab: (tab: TicketsTab) => void;
  setStatus: (status: Ticket["status"] | null) => void;
  setPage: (page: number) => void;
  reset: () => void;
}

const initial = () => ({ tab: null, status: null, page: 0 });

/** Which part of the ticket list is showing. The tickets themselves are never kept here; they come from the API. */
export const useTicketsUi = create<TicketsUi>((set) => ({
  ...initial(),
  setTab: (tab) => set({ tab, page: 0 }),
  setStatus: (status) => set({ status, page: 0 }),
  setPage: (page) => set({ page }),
  reset: () => set(initial()),
}));
