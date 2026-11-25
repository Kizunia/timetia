import { z } from "zod";

import type { Field } from "@/features/setup/resources";

export type FormValues = Record<string, string>;

/**
 * The checks of a create form, built from its fields. Every value is the text of an input or select. A batch belongs to a lab
 * or tutorial assignment and not to theory, which is the one rule that looks at two fields.
 */
export function schemaFor(fields: readonly Field[]) {
  const shape: Record<string, z.ZodType<string, string>> = {};
  for (const field of fields) {
    let text = z.string().trim();
    if (field.required) text = text.min(1, `${field.type === "select" ? "Choose" : "Enter"} the ${field.label.toLowerCase()}`);
    if (field.maxLength) text = text.max(field.maxLength, `Use at most ${field.maxLength} characters`);
    shape[field.name] =
      field.type === "number" ? text.regex(/^([1-9]\d{0,3})?$/, "Enter a whole number from 1 to 9999") : text;
  }
  return z.object(shape).superRefine((values, ctx) => {
    if ("sessionType" in values && "batchId" in values && values.sessionType && values.sessionType !== "THEORY" && !values.batchId) {
      ctx.addIssue({ code: "custom", path: ["batchId"], message: "Choose the batch" });
    }
  });
}

/** The request body: empty optional values are left out, numbers are sent as numbers, and theory sends no batch. */
export function bodyOf(fields: readonly Field[], values: FormValues): Record<string, unknown> {
  const body: Record<string, unknown> = {};
  for (const field of fields) {
    const value = (values[field.name] ?? "").trim();
    if (!value) continue;
    if (field.name === "batchId" && values.sessionType === "THEORY") continue;
    body[field.name] = field.type === "number" ? Number(value) : value;
  }
  return body;
}
