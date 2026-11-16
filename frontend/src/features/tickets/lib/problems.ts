import type { components } from "@/lib/api/schema.gen";
import { ApiError } from "@/lib/api/problem";

type ConflictType = components["schemas"]["ConflictResponse"]["type"];

/** The server names a field of the request body; the forms name their own fields. */
const FIELD_MAP: Record<string, string> = {
  "target.roomId": "roomId",
  "target.endTime": "endHour",
  "target.startTime": "startHour",
  "target.date": "date",
  "target.meetingLink": "meetingLink",
  "target.mode": "mode",
  // "No change" and "required" are about the target as a whole; the date is where the person looks first.
  target: "date",
};

/**
 * The same error with the field names of the forms, so that `applyProblem` can put each message under its field. Anything
 * that is not an API error, or has no field errors, is returned as it is.
 */
export function remap(error: unknown): unknown {
  if (!(error instanceof ApiError) || !error.problem?.errors) return error;
  return new ApiError(error.status, {
    ...error.problem,
    errors: error.problem.errors.map((fieldError) => ({ ...fieldError, field: FIELD_MAP[fieldError.field] ?? fieldError.field })),
  });
}

export const BLOCKED = "rescheduling.blocked_by_conflicts";

/**
 * What stood in the way when a ticket could not be applied: the 409 carries one error for each conflict, with the type in
 * lower case as its code. Empty for any other error.
 */
export function conflictsOf(error: unknown): { type: ConflictType; message: string }[] {
  if (!(error instanceof ApiError) || error.code !== BLOCKED) return [];
  return (error.problem?.errors ?? []).map((fieldError) => ({
    type: fieldError.code.toUpperCase() as ConflictType,
    message: fieldError.message,
  }));
}
