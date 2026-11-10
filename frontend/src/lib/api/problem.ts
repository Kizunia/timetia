import type { components } from "./schema.gen";

/** An RFC 9457 problem as the Spring API sends it (ADR 0043); the shape comes from the generated description. */
export type Problem = components["schemas"]["Problem"];

/** One invalid field in a validation failure. */
export type FieldError = NonNullable<Problem["errors"]>[number];

/** A call the API refused. `problem` is absent when the body was not a problem (a proxy or network error page). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem?: Problem,
  ) {
    super(problem ? `The API answered ${status} ${problem.code}` : `The API answered ${status}`);
    this.name = "ApiError";
  }

  get code(): string | undefined {
    return this.problem?.code;
  }

  get traceId(): string | undefined {
    return this.problem?.traceId;
  }
}

export function isProblem(value: unknown): value is Problem {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return typeof candidate.code === "string" && typeof candidate.status === "number" && typeof candidate.title === "string";
}

/**
 * Validation errors keyed by field, ready to put on a form. The first message for a field wins. Fields the form does
 * not have can still be shown from `problem.detail`.
 */
export function fieldErrors(problem: Problem | undefined): Record<string, FieldError> {
  const byField: Record<string, FieldError> = {};
  for (const error of problem?.errors ?? []) {
    byField[error.field] ??= error;
  }
  return byField;
}

/**
 * The user-facing text for a problem code. The backend's `detail` is a developer default (ADR 0043); the frontend
 * chooses its own words from the `code`. Modules add their codes here as they add screens.
 */
const MESSAGES: Record<string, string> = {
  "platform.unauthenticated": "Your session has ended. Sign in again.",
  "platform.forbidden": "You are not allowed to do that.",
  "platform.not_found": "That could not be found.",
  "platform.validation_failed": "Some of the information is not valid.",
  "platform.concurrent_modification": "Someone else changed this while you were editing. Reload it and try again.",
  "platform.duplicate": "That already exists.",
  "platform.rate_limited": "Too many requests. Wait a moment and try again.",
  "platform.payload_too_large": "That is too large to send.",
  "platform.internal_error": "Something went wrong on our side.",
  "platform.email_not_verified": "Verify your email address to do this.",
  "platform.service_unavailable": "A service we depend on is not available right now. Try again in a moment.",
  "identity.account_disabled": "Your account is disabled. Ask an administrator.",
  "identity.user_not_found": "That user could not be found.",
  "identity.email_in_use": "Someone with this email address already has an account.",
  "identity.cannot_change_self": "You cannot do this to your own account.",
  "identity.not_invited": "That user has already accepted the invitation.",
  "identity.not_disabled": "That user is not disabled.",
  "identity.not_deleted": "Delete the user before erasing them.",
  "identity.erasure_begun": "Erasing this user has already begun.",
  "identity.already_verified": "Your email address is already verified. Choose “I have verified” to continue.",
  "identity.avatar_not_found": "There is no photo.",
  "files.too_large": "That photo is larger than 5 MB. Choose a smaller one.",
  "files.unsupported_type": "Upload a JPEG, PNG or WebP image.",
  "files.invalid_image": "That file could not be read as an image. Choose a different photo.",
  "files.disabled": "Photos are switched off in this installation.",
  "access.role_not_found": "That role could not be found.",
  "access.role_name_in_use": "Another role already has this name.",
  "access.role_protected": "That role is built in and cannot be changed that way.",
  "access.role_in_use": "Users still hold this role. Take it away from them first.",
  "access.last_administrator": "This is the last administrator who can sign in. Make someone else an administrator first.",
  "access.user_not_found": "That user could not be found.",
  "notes.not_found": "That note could not be found.",
  "notes.duplicate_title": "You already have a note with this title.",
};

export const GENERIC_MESSAGE = "The request failed.";

export function messageFor(error: unknown): string {
  if (error instanceof ApiError && error.code) return MESSAGES[error.code] ?? GENERIC_MESSAGE;
  return GENERIC_MESSAGE;
}
