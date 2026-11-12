import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { vi } from "vitest";

import { PermissionsProvider } from "@/features/identity/permissions";
import type { Me } from "@/lib/api/client";

/** A signed-in user with the given permissions, for the components that adapt to what the user may do. */
export function meWith(permissions: string[], overrides: Partial<Me> = {}): Me {
  return {
    id: "11111111-1111-7111-8111-111111111111",
    username: "alice",
    email: "alice@kibootia.test",
    emailVerified: true,
    name: "Alice Example",
    status: "ACTIVE",
    theme: "SYSTEM",
    permissions,
    roles: [{ id: "r-user", name: "User", builtIn: true }],
    avatarVersion: null,
    avatarsEnabled: true,
    filesEnabled: true,
    administrator: false,
    ...overrides,
  };
}

/** Renders a screen with what it needs above it: a query client that does not retry and the user's permissions. */
export function renderScreen(ui: React.ReactElement, me: Me = meWith([])) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const view = render(
    <QueryClientProvider client={client}>
      <PermissionsProvider me={me}>{ui}</PermissionsProvider>
    </QueryClientProvider>,
  );
  return { client, ...view };
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

/** An error answer in the API's problem shape (ADR 0043). */
export function problem(status: number, code: string, extra: Record<string, unknown> = {}): Response {
  return json({ type: "about:blank", title: "Problem", status, code, ...extra }, status);
}

/** JSON bodies are parsed; anything else, such as an upload, is kept as text. */
function parseBody(text: string): unknown {
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export interface Call {
  method: string;
  path: string;
  /** The query string of the call, for the screens whose behaviour is a list's filters. */
  query: URLSearchParams;
  body: unknown;
  headers: Headers;
}

/**
 * Replaces `fetch` for a test. The handler sees each call as method, path and parsed body and answers like the API would;
 * every call is recorded so the test can check what the screen sent.
 */
export function stubApi(handler: (call: Call) => Response | Promise<Response>) {
  const calls: Call[] = [];
  const stub = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    // The code under test sometimes calls fetch with an address relative to the page, as a browser lets it.
    const request =
      input instanceof Request ? input : new Request(typeof input === "string" ? new URL(input, "http://localhost") : input, init);
    const text = ["GET", "HEAD", "DELETE"].includes(request.method) ? "" : await request.clone().text();
    const call: Call = {
      method: request.method,
      path: new URL(request.url, "http://localhost").pathname,
      query: new URL(request.url, "http://localhost").searchParams,
      body: parseBody(text),
      headers: request.headers,
    };
    calls.push(call);
    return handler(call);
  });
  vi.stubGlobal("fetch", stub);
  return { calls, stub };
}
