import { z } from "zod";

/**
 * The form's own checks, for usability; the server's validation is authoritative and its messages are shown too. A zod object
 * that has a refinement cannot be extended, so the shape of the slot and the checks on it are kept apart and put together
 * where a form needs them.
 */
const text = (max: number) => z.string().max(max, `Use at most ${max} characters`).optional();

const slotShape = {
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Choose a date"),
  startHour: z.number().int().min(8).max(19),
  endHour: z.number().int().min(9).max(20),
  mode: z.enum(["IN_PERSON", "ONLINE"]),
  roomId: z.string().optional(),
  meetingLink: z.string().max(500, "Use at most 500 characters").optional(),
};

interface SlotLike {
  startHour: number;
  endHour: number;
  mode: "IN_PERSON" | "ONLINE";
  roomId?: string;
}

/** Adds the checks that look at more than one field of a slot. */
function slotChecks(slot: SlotLike, ctx: z.RefinementCtx) {
  if (slot.endHour <= slot.startHour) ctx.addIssue({ code: "custom", path: ["endHour"], message: "End must be after start" });
  if (slot.mode === "IN_PERSON" && !slot.roomId) ctx.addIssue({ code: "custom", path: ["roomId"], message: "Choose a room" });
}

export const slotSchema = z.object(slotShape).superRefine(slotChecks);

/** Moving a lecture, once or for good. The length is fixed by the lecture, which the form enforces by locking the end hour. */
export const rescheduleSchema = z
  .object({ ...slotShape, description: text(1000), reason: text(500) })
  .superRefine(slotChecks);

export const permanentSchema = rescheduleSchema;

export const cancelSchema = z.object({
  reason: z.string().trim().min(1, "Say why").max(500, "Use at most 500 characters"),
  description: text(1000),
});

export const substituteSchema = z.object({
  substituteFacultyId: z.string().min(1, "Choose a teacher"),
  reason: text(500),
  description: text(1000),
});

export const exchangeSchema = z.object({
  otherLectureId: z.string().min(1, "Choose a lecture"),
  description: text(1000),
});

/** An extra lecture: which subject, and when and where. The end hour is free to choose. */
export const extraSchema = z
  .object({ assignmentId: z.string().min(1, "Choose a subject"), ...slotShape, description: text(1000) })
  .superRefine(slotChecks);

export const reviewSchema = z
  .object({
    decision: z.enum(["APPROVE", "REJECT", "ACKNOWLEDGE"]),
    reason: text(500),
  })
  .superRefine((review, ctx) => {
    if (review.decision === "REJECT" && !review.reason?.trim()) {
      ctx.addIssue({ code: "custom", path: ["reason"], message: "Say why" });
    }
  });

/** The people chosen to review, by user id; the dialog sends each with the capacity it was offered in. */
export const submitSchema = z.object({
  reviewerIds: z.array(z.string()).min(1, "Choose at least one person"),
});

export type RescheduleValues = z.input<typeof rescheduleSchema>;
export type ReviewValues = z.input<typeof reviewSchema>;
