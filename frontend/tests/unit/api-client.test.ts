import { describe, expect, it } from "vitest";

import { browserApi, serverApi, unwrap } from "@/lib/api/client";
import { CSRF_HEADER, CSRF_HEADER_VALUE } from "@/lib/csrf";
import { ApiError } from "@/lib/api/problem";

const me = { id: "11111111-1111-7111-8111-111111111111", username: "demo", email: null, emailVerified: true, name: null };

function recording(response: () => Response) {
  const requests: Request[] = [];
  return {
    requests,
    fetch: async (request: Request) => {
      requests.push(request);
      return response();
    },
  };
}

const json = (body: unknown, init?: ResponseInit) =>
  new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" }, ...init });

describe("serverApi", () => {
  it("calls the Spring API with the bearer token and the CSRF header", async () => {
    const api = recording(() => json(me));

    const result = await serverApi("http://api.test", "token-123", api.fetch).GET("/api/v1/me");

    expect(api.requests).toHaveLength(1);
    expect(api.requests[0]?.url).toBe("http://api.test/api/v1/me");
    expect(api.requests[0]?.method).toBe("GET");
    expect(api.requests[0]?.headers.get("Authorization")).toBe("Bearer token-123");
    expect(api.requests[0]?.headers.get(CSRF_HEADER)).toBe(CSRF_HEADER_VALUE);
    expect(unwrap(result)).toEqual(me);
  });

  it("sends the path and query of a list call as the description says", async () => {
    const api = recording(() => json({ items: [], page: { number: 0, size: 20, totalElements: 0, totalPages: 0 } }));

    await serverApi("http://api.test", "t", api.fetch).GET("/api/v1/notes", {
      params: { query: { page: 1, size: 5, sort: "title,asc", q: "milk" } },
    });

    const url = new URL(api.requests[0]!.url);
    expect(url.pathname).toBe("/api/v1/notes");
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: "1", size: "5", sort: "title,asc", q: "milk" });
  });

  it("puts path parameters into the URL and the body into the request", async () => {
    const api = recording(() => json({ ...me }));
    const id = "22222222-2222-7222-8222-222222222222";

    await serverApi("http://api.test", "t", api.fetch).PUT("/api/v1/notes/{id}", {
      params: { path: { id } },
      body: { title: "T", body: "B", version: 3 },
    });

    expect(api.requests[0]?.url).toBe(`http://api.test/api/v1/notes/${id}`);
    expect(api.requests[0]?.method).toBe("PUT");
    expect(await api.requests[0]?.json()).toEqual({ title: "T", body: "B", version: 3 });
  });
});

describe("browserApi", () => {
  it("calls the same-origin proxy with the CSRF header and no token", async () => {
    const api = recording(() => json(me));

    await browserApi("http://app.test", api.fetch).GET("/api/v1/me");

    expect(api.requests[0]?.url).toBe("http://app.test/api/v1/me");
    expect(api.requests[0]?.headers.get("Authorization")).toBeNull();
    expect(api.requests[0]?.headers.get(CSRF_HEADER)).toBe(CSRF_HEADER_VALUE);
  });
});

describe("unwrap", () => {
  it("turns a problem response into an ApiError with the status, code and trace ID", async () => {
    const problem = { title: "Conflict", status: 409, code: "notes.duplicate_title", traceId: "abc" };
    const api = recording(() => json(problem, { status: 409, headers: { "Content-Type": "application/problem+json" } }));

    const result = await serverApi("http://api.test", "t", api.fetch).POST("/api/v1/notes", { body: { title: "T", body: "B" } });

    const error = captured(() => unwrap(result));
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(409);
    expect(error.code).toBe("notes.duplicate_title");
    expect(error.traceId).toBe("abc");
  });

  it("keeps the status when the body is not a problem, such as a gateway error page", async () => {
    const api = recording(() => new Response("<html>Bad gateway</html>", { status: 502, headers: { "Content-Type": "text/html" } }));

    const result = await serverApi("http://api.test", "t", api.fetch).GET("/api/v1/me");

    const error = captured(() => unwrap(result));
    expect(error.status).toBe(502);
    expect(error.problem).toBeUndefined();
  });

  it("treats a 401 with no body as an error too", async () => {
    const api = recording(() => new Response(null, { status: 401 }));

    const result = await serverApi("http://api.test", "t", api.fetch).GET("/api/v1/me");

    expect(captured(() => unwrap(result)).status).toBe(401);
  });

  it("returns nothing for a successful call without a body", async () => {
    const api = recording(() => new Response(null, { status: 204 }));

    const result = await serverApi("http://api.test", "t", api.fetch).DELETE("/api/v1/notes/{id}", {
      params: { path: { id: "22222222-2222-7222-8222-222222222222" } },
    });

    expect(unwrap(result)).toBeUndefined();
  });
});

function captured(call: () => unknown): ApiError {
  try {
    call();
  } catch (error) {
    return error as ApiError;
  }
  throw new Error("Expected the call to throw");
}
