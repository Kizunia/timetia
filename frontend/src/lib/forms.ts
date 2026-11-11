import { ApiError, fieldErrors, messageFor } from "@/lib/api/problem";

/**
 * Puts what the API said about a failed submit where the user can act on it (ADR 0038, ADR 0043). Validation errors name a
 * field and go on that field; a few business codes belong to one field and go there too; anything else is returned as a
 * message for the form to show on its own. The server's validation is authoritative, so these appear even when the form's own
 * checks passed.
 *
 * @param fields the form's field names
 * @param codeToField business problem codes that belong to a field, for example `identity.email_in_use` to `email`
 * @returns the message the form should show, or null when every part of the failure found its field
 */
export function applyProblem(
  error: unknown,
  fields: readonly string[],
  setFieldError: (field: string, message: string) => void,
  codeToField: Record<string, string> = {},
): string | null {
  if (!(error instanceof ApiError)) return messageFor(error);
  let placed = false;
  for (const [field, problem] of Object.entries(fieldErrors(error.problem))) {
    if (fields.includes(field)) {
      setFieldError(field, problem.message);
      placed = true;
    }
  }
  const field = error.code ? codeToField[error.code] : undefined;
  if (field && fields.includes(field)) {
    setFieldError(field, messageFor(error));
    placed = true;
  }
  return placed ? null : messageFor(error);
}
