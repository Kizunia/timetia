import { describe, expect, it, vi } from "vitest";

import { proxyApi } from "@/lib/bff/proxy";

import { makeDeps, request, sessionCookie } from "./helpers";

/**
 * Files go through the generic `/api` proxy of ADR 0042 with no special case (ADR 0029): an upload is streamed to Spring
 * as it arrives, an image is streamed back as it is, and a conditional request reaches Spring so the permission is
 * checked on every view.
 */

const PNG = Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0xff, 0x10, 0x80]);
const BOUNDARY = "----kibootia-boundary-1234";

function multipart(): { body: Uint8Array; contentType: string } {
  const head = new TextEncoder().encode(
    `--${BOUNDARY}\r\nContent-Disposition: form-data; name="file"; filename="me.png"\r\nContent-Type: image/png\r\n\r\n`,
  );
  const tail = new TextEncoder().encode(`\r\n--${BOUNDARY}--\r\n`);
  const body = new Uint8Array(head.length + PNG.length + tail.length);
  body.set(head, 0);
  body.set(PNG, head.length);
  body.set(tail, head.length + PNG.length);
  return { body, contentType: `multipart/form-data; boundary=${BOUNDARY}` };
}

describe("/api proxy: uploads", () => {
  it("streams a multipart body to Spring byte for byte, with its content type and boundary untouched", async () => {
    const { body, contentType } = multipart();
    let received = new Uint8Array();
    let init: (RequestInit & { duplex?: string }) | undefined;
    const upstream = vi.fn(async (_url: unknown, options: RequestInit & { duplex?: string }) => {
      init = options;
      received = new Uint8Array(await new Response(options.body as ReadableStream).arrayBuffer());
      return Response.json({ id: "u1" });
    });

    const response = await proxyApi(
      request("/api/v1/me/avatar", {
        method: "PUT",
        body: body as BodyInit,
        headers: { "content-type": contentType },
        csrf: true,
        cookie: await sessionCookie(),
      }),
      makeDeps({ fetch: upstream as unknown as typeof fetch }),
    );

    expect(response.status).toBe(200);
    expect(received).toEqual(body);
    expect(new Headers(init?.headers).get("content-type")).toBe(contentType);
    expect(init?.duplex).toBe("half");
    expect(init?.body).toBeInstanceOf(ReadableStream);
  });

  it("does not let an upload through without the CSRF header, so no site can upload as the user", async () => {
    const { body, contentType } = multipart();
    const upstream = vi.fn(async () => Response.json({}));

    const response = await proxyApi(
      request("/api/v1/me/avatar", {
        method: "PUT",
        body: body as BodyInit,
        headers: { "content-type": contentType },
        cookie: await sessionCookie(),
      }),
      makeDeps({ fetch: upstream as unknown as typeof fetch }),
    );

    expect(response.status).toBe(403);
    expect(upstream).not.toHaveBeenCalled();
  });

  it("passes Spring's 413 for a file that is too large to the browser unchanged", async () => {
    const upstream = vi.fn(async () =>
      Response.json({ code: "files.too_large", status: 413, title: "Payload Too Large" }, { status: 413 }),
    );

    const response = await proxyApi(
      request("/api/v1/me/avatar", { method: "PUT", body: "x", csrf: true, cookie: await sessionCookie() }),
      makeDeps({ fetch: upstream as unknown as typeof fetch }),
    );

    expect(response.status).toBe(413);
    expect((await response.json()).code).toBe("files.too_large");
  });
});

describe("/api proxy: images", () => {
  it("streams an image back unchanged, with its type, entity tag and cache rule, and drops any cookie Spring sets", async () => {
    const upstream = vi.fn(
      async () =>
        new Response(PNG, {
          status: 200,
          headers: {
            "Content-Type": "image/png",
            ETag: '"abc-large"',
            "Cache-Control": "no-cache, private",
            "X-Content-Type-Options": "nosniff",
            "Set-Cookie": "tracking=1",
          },
        }),
    );

    const response = await proxyApi(
      request("/api/v1/users/u1/avatar?size=LARGE", { cookie: await sessionCookie() }),
      makeDeps({ fetch: upstream as unknown as typeof fetch }),
    );

    expect(response.status).toBe(200);
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(PNG);
    expect(response.headers.get("content-type")).toBe("image/png");
    expect(response.headers.get("etag")).toBe('"abc-large"');
    expect(response.headers.get("cache-control")).toBe("no-cache, private");
    expect(response.headers.get("x-content-type-options")).toBe("nosniff");
    expect(response.headers.getSetCookie().join(";")).not.toContain("tracking");
  });

  it("sends the browser's If-None-Match to Spring and passes a 304 back with its entity tag and no body", async () => {
    let seen: Headers | undefined;
    const upstream = vi.fn(async (_url: unknown, options: RequestInit) => {
      seen = new Headers(options.headers);
      return new Response(null, { status: 304, headers: { ETag: '"abc-large"' } });
    });

    const response = await proxyApi(
      request("/api/v1/users/u1/avatar?size=LARGE", {
        headers: { "if-none-match": '"abc-large"' },
        cookie: await sessionCookie(),
      }),
      makeDeps({ fetch: upstream as unknown as typeof fetch }),
    );

    expect(seen?.get("if-none-match")).toBe('"abc-large"');
    expect(response.status).toBe(304);
    expect(response.headers.get("etag")).toBe('"abc-large"');
    expect(await response.text()).toBe("");
  });

  it("passes Spring's refusal to show someone else's avatar, so a cached copy never overrides the permission", async () => {
    const upstream = vi.fn(async () => Response.json({ code: "platform.forbidden", status: 403, title: "Forbidden" }, { status: 403 }));

    const response = await proxyApi(
      request("/api/v1/users/u2/avatar", { cookie: await sessionCookie() }),
      makeDeps({ fetch: upstream as unknown as typeof fetch }),
    );

    expect(response.status).toBe(403);
  });
});
