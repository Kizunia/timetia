import type { ReviewerCandidate } from "@/features/tickets/api";

/** The order the groups are shown in. */
export const CAPACITY_ORDER: readonly ReviewerCandidate["capacity"][] = ["HOD", "CR", "BR", "AFFECTED_FACULTY", "OTHER_FACULTY"];

/** The request's reviewers for the people chosen: each goes with the capacity they were offered in. */
export function toReviewers(candidates: readonly ReviewerCandidate[], chosen: readonly string[]) {
  return candidates
    .filter((candidate) => chosen.includes(candidate.userId))
    .map((candidate) => ({ userId: candidate.userId, capacity: candidate.capacity }));
}

/** Whether an HOD is among the people chosen. A permanent change cannot be sent without one. */
export function hasHod(candidates: readonly ReviewerCandidate[], chosen: readonly string[]): boolean {
  return candidates.some((candidate) => candidate.capacity === "HOD" && chosen.includes(candidate.userId));
}

/** The candidates in groups by capacity, in the order of {@link CAPACITY_ORDER}; a capacity nobody has is left out. */
export function groupByCapacity(candidates: readonly ReviewerCandidate[]) {
  return CAPACITY_ORDER.map((capacity) => ({
    capacity,
    people: candidates.filter((candidate) => candidate.capacity === capacity),
  })).filter((group) => group.people.length > 0);
}
