"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { browserApi, unwrap } from "@/lib/api/client";
import type { components } from "@/lib/api/schema.gen";
import { addDays, type IsoDate } from "@/lib/dates";

export type Lecture = components["schemas"]["LiveLectureResponse"];

export interface LiveFilters {
  division?: string | null;
  batch?: string | null;
  faculty?: string | null;
}

const PAGE_SIZE = 100;

/**
 * Every lecture from one date to another (inclusive), read page by page until the last. The server refuses a range of more
 * than 62 days. A filter that is null is not sent. With `enabled` false nothing is requested.
 */
export function useLiveRange(from: IsoDate, to: IsoDate, filters: LiveFilters = {}, enabled = true) {
  const { division = null, batch = null, faculty = null } = filters;
  return useQuery({
    enabled,
    queryKey: ["live", { from, to, division, batch, faculty }],
    queryFn: async (): Promise<Lecture[]> => {
      const lectures: Lecture[] = [];
      for (let page = 0; ; page += 1) {
        const query = {
          from,
          to,
          page,
          size: PAGE_SIZE,
          ...(division ? { division } : {}),
          ...(batch ? { batch } : {}),
          ...(faculty ? { faculty } : {}),
        };
        const result = unwrap(await browserApi().GET("/api/v1/timetable/live", { params: { query } }));
        lectures.push(...result.items);
        if (result.page.number + 1 >= result.page.totalPages) return lectures;
      }
    },
    placeholderData: keepPreviousData,
  });
}

/** The seven days from `weekStart`. */
export function useLiveWeek(weekStart: IsoDate, filters: LiveFilters = {}) {
  return useLiveRange(weekStart, addDays(weekStart, 6), filters);
}

/** One lecture of the live timetable. Without an ID nothing is requested. */
export function useLecture(id: string | null | undefined) {
  return useQuery({
    queryKey: ["lecture", id],
    queryFn: async () => unwrap(await browserApi().GET("/api/v1/timetable/live/{id}", { params: { path: { id: id! } } })),
    enabled: Boolean(id),
  });
}
