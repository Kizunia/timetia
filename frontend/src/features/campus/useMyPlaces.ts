"use client";

import { useMyCampus, useMyScopedRoles } from "@/features/campus/api";
import { placesOf } from "@/features/campus/lib/places";

/**
 * Who the signed-in person is on campus: their faculty records, and the divisions, batches and departments they hold a
 * role in. Screens use it to preselect filters and decide which hints to show; it grants nothing.
 */
export function useMyPlaces() {
  const campus = useMyCampus();
  const scoped = useMyScopedRoles();
  const facultyIds = (campus.data?.faculty ?? []).map((faculty) => faculty.id);
  return {
    facultyIds,
    isFaculty: facultyIds.length > 0,
    ...placesOf(scoped.data),
    isLoading: campus.isLoading || scoped.isLoading,
  };
}
