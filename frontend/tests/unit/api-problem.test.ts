import { describe, expect, it } from "vitest";

import { ApiError, fieldErrors, GENERIC_MESSAGE, isProblem, messageFor, type Problem } from "@/lib/api/problem";

const validation: Problem = {
  type: "about:blank",
  title: "Validation failed",
  status: 400,
  code: "platform.validation_failed",
  traceId: "4bf92f3577b34da6a3ce929d0e0e4736",
  errors: [
    { field: "title", code: "required", message: "must not be blank" },
    { field: "title", code: "invalid_size", message: "size must be at most 120" },
    { field: "body", code: "required", message: "must not be null" },
  ],
};

describe("isProblem", () => {
  it("accepts a body with a code, a status and a title", () => {
    expect(isProblem(validation)).toBe(true);
    expect(isProblem({ code: "notes.not_found", status: 404, title: "Not Found" })).toBe(true);
  });

  it("rejects anything else", () => {
    expect(isProblem(null)).toBe(false);
    expect(isProblem(undefined)).toBe(false);
    expect(isProblem("Bad gateway")).toBe(false);
    expect(isProblem({ status: 502, title: "Bad Gateway" })).toBe(false);
    expect(isProblem({ code: 7, status: 400, title: "x" })).toBe(false);
    expect(isProblem({ code: "x", status: "400", title: "x" })).toBe(false);
  });
});

describe("ApiError", () => {
  it("carries the status, the code and the trace ID of the problem", () => {
    const error = new ApiError(400, validation);

    expect(error.status).toBe(400);
    expect(error.code).toBe("platform.validation_failed");
    expect(error.traceId).toBe("4bf92f3577b34da6a3ce929d0e0e4736");
    expect(error.message).toContain("platform.validation_failed");
    expect(error).toBeInstanceOf(Error);
  });

  it("still works when the body was not a problem", () => {
    const error = new ApiError(502);

    expect(error.status).toBe(502);
    expect(error.code).toBeUndefined();
    expect(error.traceId).toBeUndefined();
    expect(error.message).toBe("The API answered 502");
  });
});

describe("fieldErrors", () => {
  it("keys the errors by field and keeps the first one for a field", () => {
    const byField = fieldErrors(validation);

    expect(Object.keys(byField).sort()).toEqual(["body", "title"]);
    expect(byField.title?.code).toBe("required");
    expect(byField.body?.message).toBe("must not be null");
  });

  it("is empty without errors", () => {
    expect(fieldErrors(undefined)).toEqual({});
    expect(fieldErrors({ title: "Not Found", status: 404, code: "platform.not_found" })).toEqual({});
  });
});

describe("messageFor", () => {
  it("chooses its own words from the code, not the backend's detail", () => {
    const error = new ApiError(409, { title: "Conflict", status: 409, code: "notes.duplicate_title", detail: "internal text" });

    expect(messageFor(error)).toBe("You already have a note with this title.");
    expect(messageFor(error)).not.toContain("internal");
  });

  it("falls back to a generic message for an unknown code, a missing code or another kind of error", () => {
    expect(messageFor(new ApiError(500, { title: "x", status: 500, code: "future.new_code" }))).toBe(GENERIC_MESSAGE);
    expect(messageFor(new ApiError(502))).toBe(GENERIC_MESSAGE);
    expect(messageFor(new TypeError("fetch failed"))).toBe(GENERIC_MESSAGE);
    expect(messageFor(undefined)).toBe(GENERIC_MESSAGE);
  });
});
