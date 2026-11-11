import { ROOT_CONTEXT, trace, type TextMapPropagator, propagation } from "@opentelemetry/api";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";

import { isSafeApiPath, proxyApi } from "@/lib/bff/proxy";

import { fakeOidc, makeDeps, NOW_S, request, sessionCookie, setCookiesOf } from "./helpers";

type FetchMock = ReturnType<typeof vi.fn>;

function upstreamReturning(response: () => Response): { fetch: FetchMock } {
  return { fetch: vi.fn(async () => response()) };
}

function sent(mock: FetchMock): { url: URL; init: RequestInit & { duplex?: string }; headers: Headers } {
  const [url, init] = mock.mock.calls[0] as [URL, RequestInit & { duplex?: string }];
  return { url, init, headers: new Headers(init.headers) };
}

const TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
const SPAN_ID = "00f067aa0ba902b7";

beforeAll(() => {
  // A minimal W3C propagator, so the test does not depend on a particular OpenTelemetry SDK package.
  const w3c: TextMapPropagator = {
    inject(context, carrier, setter) {
      const span = trace.getSpanContext(context);
      if (span) setter.set(carrier, "traceparent", `00-${span.traceId}-${span.spanId}-0${span.traceFlags}`);
    },
    extract: (context) => context,
    fields: () => ["traceparent"],
  };
  propagation.setGlobalPropagator(w3c);
});

afterEach(() => vi.restoreAllMocks());

describe("/api proxy: authentication", () => {
  it("answers 401 when there is no session", async () => {
    const deps = makeDeps();
    const response = await proxyApi(request("/api/v1/me"), deps);
    expect(response.status).toBe(401);
    expect((await response.json()).code).toBe("bff.unauthenticated");
    expect(deps.fetch).not.toHaveBeenCalled();
  });

  it("attaches the access token from the session as a bearer token", async () => {
    const upstream = upstreamReturning(() => Response.json({ id: "u1" }));
    const response = await proxyApi(request("/api/v1/me", { cookie: await sessionCookie() }), makeDeps({ fetch: upstream.fetch as typeof fetch }));
    expect(response.status).toBe(200);
    const { url, headers } = sent(upstream.fetch);
    expect(url.href).toBe("http://api.internal:8080/api/v1/me");
    expect(headers.get("authorization")).toBe("Bearer access-1");
  });

  it("forces a new sign-in when the refresh fails, and clears the session", async () => {
    const upstream = upstreamReturning(() => Response.json({}));
    const deps = makeDeps({
      fetch: upstream.fetch as typeof fetch,
      oidc: fakeOidc({ refresh: vi.fn(async () => { throw new Error("invalid_grant"); }) }),
    });
    const expiring = await sessionCookie({ accessExpiresAt: NOW_S + 5 });
    const response = await proxyApi(request("/api/v1/me", { cookie: expiring }), deps);
    expect(response.status).toBe(401);
    expect(upstream.fetch).not.toHaveBeenCalled();
    expect(setCookiesOf(response).every((cookie) => /Max-Age=0/.test(cookie))).toBe(true);
    expect(setCookiesOf(response).length).toBeGreaterThan(0);
  });

  it("refreshes a token that is about to expire, uses the new one and stores the new session", async () => {
    const upstream = upstreamReturning(() => Response.json({}));
    const deps = makeDeps({ fetch: upstream.fetch as typeof fetch });
    const response = await proxyApi(request("/api/v1/me", { cookie: await sessionCookie({ accessExpiresAt: NOW_S + 5 }) }), deps);
    expect(response.status).toBe(200);
    expect(sent(upstream.fetch).headers.get("authorization")).toBe("Bearer access-2");
    expect(setCookiesOf(response).some((cookie) => cookie.startsWith("timetia_session.0="))).toBe(true);
  });

  it("refreshes once when several requests arrive together", async () => {
    const refresh = vi.fn(async () => {
      await new Promise((resolve) => setTimeout(resolve, 20));
      return { accessToken: "access-2", refreshToken: "refresh-2", idToken: "id-2", expiresIn: 300 };
    });
    const upstream = upstreamReturning(() => Response.json({}));
    const deps = makeDeps({ fetch: upstream.fetch as typeof fetch, oidc: fakeOidc({ refresh }) });
    const cookie = await sessionCookie({ accessExpiresAt: NOW_S + 5 });
    await Promise.all([1, 2, 3].map(() => proxyApi(request("/api/v1/me", { cookie }), deps)));
    expect(refresh).toHaveBeenCalledTimes(1);
  });
});

describe("/api proxy: CSRF", () => {
  it.each(["POST", "PUT", "PATCH", "DELETE"])("rejects %s without the Origin and header, before reaching the API", async (method) => {
    const upstream = upstreamReturning(() => Response.json({}));
    const response = await proxyApi(request("/api/v1/things", { method, cookie: await sessionCookie() }), makeDeps({ fetch: upstream.fetch as typeof fetch }));
    expect(response.status).toBe(403);
    expect(upstream.fetch).not.toHaveBeenCalled();
  });

  it("accepts a state-changing request with the right Origin and header", async () => {
    const upstream = upstreamReturning(() => new Response(null, { status: 201 }));
    const response = await proxyApi(
      request("/api/v1/things", { method: "POST", body: "{}", csrf: true, cookie: await sessionCookie() }),
      makeDeps({ fetch: upstream.fetch as typeof fetch }),
    );
    expect(response.status).toBe(201);
  });

  it("lets GET through without the header", async () => {
    const upstream = upstreamReturning(() => Response.json({}));
    const response = await proxyApi(request("/api/v1/me", { cookie: await sessionCookie() }), makeDeps({ fetch: upstream.fetch as typeof fetch }));
    expect(response.status).toBe(200);
  });

  it("answers an OPTIONS preflight with 405 and no CORS headers", async () => {
    const response = await proxyApi(request("/api/v1/me", { method: "OPTIONS" }), makeDeps());
    expect(response.status).toBe(405);
    expect(response.headers.get("access-control-allow-origin")).toBeNull();
  });
});

describe("/api proxy: streaming", () => {
  it("passes the request body on as a stream without reading it", async () => {
    const upstream = upstreamReturning(() => new Response(null, { status: 204 }));
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode("chunk"));
        controller.close();
      },
    });
    const incoming = new Request("http://localhost:3000/api/v1/upload", {
      method: "POST",
      body,
      duplex: "half",
      headers: { origin: "http://localhost:3000", "x-timetia-request": "1", cookie: await sessionCookie() },
    } as RequestInit);
    await proxyApi(incoming, makeDeps({ fetch: upstream.fetch as typeof fetch }));
    const { init } = sent(upstream.fetch);
    expect(init.body).toBeInstanceOf(ReadableStream);
    expect(init.duplex).toBe("half");
  });

  it("streams the response body chunk by chunk instead of buffering it", async () => {
    let release!: () => void;
    const secondChunkAllowed = new Promise<void>((resolve) => (release = resolve));
    const encoder = new TextEncoder();
    const upstreamBody = new ReadableStream<Uint8Array>({
      async start(controller) {
        controller.enqueue(encoder.encode("first"));
        await secondChunkAllowed;
        controller.enqueue(encoder.encode("second"));
        controller.close();
      },
    });
    const upstream = upstreamReturning(() => new Response(upstreamBody, { status: 200 }));
    const response = await proxyApi(request("/api/v1/stream", { cookie: await sessionCookie() }), makeDeps({ fetch: upstream.fetch as typeof fetch }));

    const reader = response.body!.getReader();
    // The first chunk arrives while the upstream is still open: nothing waited for the end of the body.
    expect(new TextDecoder().decode((await reader.read()).value)).toBe("first");
    release();
    expect(new TextDecoder().decode((await reader.read()).value)).toBe("second");
  });
});

describe("/api proxy: trace context", () => {
  it("passes the trace context of the current span to the API", async () => {
    const upstream = upstreamReturning(() => Response.json({}));
    const span = trace.wrapSpanContext({ traceId: TRACE_ID, spanId: SPAN_ID, traceFlags: 1 });
    const traceContext = trace.setSpan(ROOT_CONTEXT, span);
    await proxyApi(request("/api/v1/me", { cookie: await sessionCookie() }), makeDeps({ fetch: upstream.fetch as typeof fetch }), traceContext);
    expect(sent(upstream.fetch).headers.get("traceparent")).toBe(`00-${TRACE_ID}-${SPAN_ID}-01`);
  });

  it("does not forward a trace context supplied by the browser", async () => {
    const upstream = upstreamReturning(() => Response.json({}));
    const forged = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01";
    await proxyApi(
      request("/api/v1/me", { cookie: await sessionCookie(), headers: { traceparent: forged, tracestate: "x=y", baggage: "k=v" } }),
      makeDeps({ fetch: upstream.fetch as typeof fetch }),
      ROOT_CONTEXT,
    );
    const { headers } = sent(upstream.fetch);
    expect(headers.get("traceparent")).toBeNull();
    expect(headers.get("tracestate")).toBeNull();
    expect(headers.get("baggage")).toBeNull();
  });
});

describe("/api proxy: header and cookie handling", () => {
  it("does not send cookies, the browser's own authorization or its origin data to the API", async () => {
    const upstream = upstreamReturning(() => Response.json({}));
    await proxyApi(
      request("/api/v1/me", {
        cookie: await sessionCookie(),
        headers: { authorization: "Bearer from-browser", referer: "http://localhost:3000/me", "x-forwarded-for": "6.6.6.6", accept: "application/json", "accept-language": "en" },
      }),
      makeDeps({ fetch: upstream.fetch as typeof fetch }),
    );
    const { headers } = sent(upstream.fetch);
    expect(headers.get("cookie")).toBeNull();
    expect(headers.get("authorization")).toBe("Bearer access-1");
    expect(headers.get("referer")).toBeNull();
    expect(headers.get("x-forwarded-for")).toBeNull();
    expect(headers.get("accept")).toBe("application/json");
    expect(headers.get("accept-language")).toBe("en");
  });

  it("drops hop-by-hop request headers", async () => {
    const upstream = upstreamReturning(() => Response.json({}));
    await proxyApi(
      request("/api/v1/me", { cookie: await sessionCookie(), headers: { te: "trailers", upgrade: "websocket", "keep-alive": "timeout=5", "proxy-authorization": "Basic x" } }),
      makeDeps({ fetch: upstream.fetch as typeof fetch }),
    );
    const { headers } = sent(upstream.fetch);
    for (const name of ["te", "upgrade", "keep-alive", "proxy-authorization"]) expect(headers.get(name)).toBeNull();
  });

  it("ignores cookies the API tries to set and strips hop-by-hop response headers", async () => {
    const upstream = upstreamReturning(
      () =>
        new Response("{}", {
          status: 200,
          headers: { "Set-Cookie": "JSESSIONID=abc; Path=/", "Keep-Alive": "timeout=5", "X-Custom": "kept", "Content-Type": "application/json" },
        }),
    );
    const response = await proxyApi(request("/api/v1/me", { cookie: await sessionCookie() }), makeDeps({ fetch: upstream.fetch as typeof fetch }));
    expect(setCookiesOf(response)).toEqual([]);
    expect(response.headers.get("keep-alive")).toBeNull();
    expect(response.headers.get("x-custom")).toBe("kept");
    expect(response.headers.get("content-type")).toBe("application/json");
  });

  it("passes the API's status and error body through", async () => {
    const upstream = upstreamReturning(() => Response.json({ code: "x.y" }, { status: 422, headers: { "Content-Type": "application/problem+json" } }));
    const response = await proxyApi(request("/api/v1/me", { cookie: await sessionCookie() }), makeDeps({ fetch: upstream.fetch as typeof fetch }));
    expect(response.status).toBe(422);
    expect((await response.json()).code).toBe("x.y");
  });

  it("does not follow redirects from the API", async () => {
    const upstream = upstreamReturning(() => new Response(null, { status: 302, headers: { Location: "http://elsewhere.example/" } }));
    await proxyApi(request("/api/v1/me", { cookie: await sessionCookie() }), makeDeps({ fetch: upstream.fetch as typeof fetch }));
    expect(sent(upstream.fetch).init.redirect).toBe("manual");
  });

  it("answers 502 when the API cannot be reached", async () => {
    const deps = makeDeps({ fetch: vi.fn(async () => { throw new TypeError("fetch failed"); }) as unknown as typeof fetch });
    const response = await proxyApi(request("/api/v1/me", { cookie: await sessionCookie() }), deps);
    expect(response.status).toBe(502);
    expect((await response.json()).code).toBe("bff.upstream_unavailable");
  });
});

describe("/api proxy: fixed upstream", () => {
  it.each([
    "/api/v1/me",
    "/api/v1/things/42",
    "/api/a.b/c",
  ])("accepts %s", (path) => expect(isSafeApiPath(path)).toBe(true));

  it.each([
    "/api",
    "/api/",
    "/api/../admin",
    "/api/v1/../../admin",
    "/api/%2e%2e/admin",
    "/api/v1/%2E%2E/%2E%2E/admin",
    "/api/v1%2F..%2Fadmin",
    "/api/v1/a%5Cb",
    "/api/v1/%00",
    "/other/v1/me",
  ])("rejects %s", (path) => expect(isSafeApiPath(path)).toBe(false));

  it("never sends a rejected path to the API", async () => {
    const upstream = upstreamReturning(() => Response.json({}));
    const incoming = new Request("http://localhost:3000/api/v1/%2e%2e/%2e%2e/admin", { headers: { cookie: await sessionCookie() } });
    const response = await proxyApi(incoming, makeDeps({ fetch: upstream.fetch as typeof fetch }));
    expect(response.status).toBe(400);
    expect(upstream.fetch).not.toHaveBeenCalled();
  });

  it("always targets the configured API origin", async () => {
    const upstream = upstreamReturning(() => Response.json({}));
    const incoming = new Request("http://localhost:3000/api/v1/me?x=http://evil.example", { headers: { cookie: await sessionCookie(), host: "evil.example" } });
    await proxyApi(incoming, makeDeps({ fetch: upstream.fetch as typeof fetch }));
    expect(sent(upstream.fetch).url.origin).toBe("http://api.internal:8080");
    expect(sent(upstream.fetch).url.search).toBe("?x=http://evil.example");
  });
});
