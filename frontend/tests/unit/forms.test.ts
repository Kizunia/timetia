import { describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api/problem";
import { applyProblem } from "@/lib/forms";

const validation = new ApiError(400, {
  type: "about:blank",
  title: "Validation failed",
  status: 400,
  code: "platform.validation_failed",
  errors: [
    { field: "email", code: "invalid_format", message: "must be a valid email address" },
    { field: "email", code: "required", message: "must not be blank" },
    { field: "unknown", code: "x", message: "not on this form" },
  ],
});

describe("showing a failed submit where the user can act on it", () => {
  it("puts each validation error on the field it names, the first message winning", () => {
    const set = vi.fn();

    const message = applyProblem(validation, ["email", "roleIds"], set);

    expect(set).toHaveBeenCalledOnce();
    expect(set).toHaveBeenCalledWith("email", "must be a valid email address");
    expect(message).toBeNull();
  });

  it("returns a message when no part of the failure belongs to a field", () => {
    const set = vi.fn();

    expect(applyProblem(validation, ["name"], set)).toBe("Some of the information is not valid.");
    expect(set).not.toHaveBeenCalled();
  });

  it("places a business code on its field, in words chosen from the code", () => {
    const set = vi.fn();
    const taken = new ApiError(409, { type: "about:blank", title: "Conflict", status: 409, code: "identity.email_in_use" });

    const message = applyProblem(taken, ["email"], set, { "identity.email_in_use": "email" });

    expect(set).toHaveBeenCalledWith("email", "Someone with this email address already has an account.");
    expect(message).toBeNull();
  });

  it("does not place a code on a field the form does not have", () => {
    const set = vi.fn();
    const taken = new ApiError(409, { type: "about:blank", title: "Conflict", status: 409, code: "identity.email_in_use" });

    expect(applyProblem(taken, ["name"], set, { "identity.email_in_use": "email" })).toBe(
      "Someone with this email address already has an account.",
    );
    expect(set).not.toHaveBeenCalled();
  });

  it("gives the generic text for a failure that was not an API answer, such as a lost connection", () => {
    expect(applyProblem(new TypeError("fetch failed"), ["email"], vi.fn())).toBe("The request failed.");
  });
});
