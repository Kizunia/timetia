import { z } from "zod";

import type { NewIssue } from "@/features/issues/api";

export const MAX_MENTIONS = 10;

/** The form's own checks, for usability; the server's validation is authoritative and its messages are shown too. */
export const issueSchema = z
  .object({
    sessionType: z.enum(["THEORY", "LAB", "TUTORIAL"]),
    divisionId: z.string().optional(),
    batchId: z.string().optional(),
    title: z.string().trim().min(1, "Enter a title").max(200, "Use at most 200 characters"),
    description: z.string().max(2000, "Use at most 2000 characters"),
    mentionedFacultyIds: z.array(z.string()).max(MAX_MENTIONS, `Mention at most ${MAX_MENTIONS} teachers`),
  })
  .superRefine((issue, ctx) => {
    if (issue.sessionType === "THEORY" && !issue.divisionId) {
      ctx.addIssue({ code: "custom", path: ["divisionId"], message: "Choose a division" });
    }
    if (issue.sessionType !== "THEORY" && !issue.batchId) {
      ctx.addIssue({ code: "custom", path: ["batchId"], message: "Choose a batch" });
    }
  });

export type IssueValues = z.infer<typeof issueSchema>;

/** The request body: a theory issue names its division and a lab or tutorial issue its batch, never both. */
export function issueBody(values: IssueValues): NewIssue {
  const description = values.description.trim();
  return {
    sessionType: values.sessionType,
    title: values.title.trim(),
    ...(description ? { description } : {}),
    ...(values.sessionType === "THEORY" ? { divisionId: values.divisionId } : { batchId: values.batchId }),
    mentionedFacultyIds: values.mentionedFacultyIds,
  };
}
