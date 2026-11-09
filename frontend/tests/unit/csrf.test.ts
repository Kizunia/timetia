import { describe, expect, it } from "vitest";

import { checkCsrf } from "@/lib/csrf";

import { config } from "./helpers";

const base = config.publicBaseUrl;

function req(method: string, headers: Record<string, string> = {}) {
  return new Request(`${base}/api/v1/things`, { method, headers });
}

describe("CSRF rule (ADR 0042)", () => {
  it.each(["GET", "HEAD"])("lets %s through without any header", (method) => {
    expect(checkCsrf(req(method), base)).toBeNull();
  });

  it.each(["POST", "PUT", "PATCH", "DELETE"])("accepts %s with the right Origin and the custom header", (method) => {
    expect(checkCsrf(req(method, { origin: base, "x-kibootia-request": "1" }), base)).toBeNull();
  });

  it("rejects a state-changing request without an Origin", async () => {
    const rejected = checkCsrf(req("POST", { "x-kibootia-request": "1" }), base);
    expect(rejected?.status).toBe(403);
    expect((await rejected!.json()).code).toBe("bff.csrf_origin");
  });

  it("rejects a request from another origin", () => {
    const rejected = checkCsrf(req("POST", { origin: "https://evil.example", "x-kibootia-request": "1" }), base);
    expect(rejected?.status).toBe(403);
  });

  it("rejects an origin that only starts like ours", () => {
    const rejected = checkCsrf(req("POST", { origin: `${base}.evil.example`, "x-kibootia-request": "1" }), base);
    expect(rejected?.status).toBe(403);
  });

  it("rejects the null origin", () => {
    expect(checkCsrf(req("POST", { origin: "null", "x-kibootia-request": "1" }), base)?.status).toBe(403);
  });

  it("rejects a request without the custom header", async () => {
    const rejected = checkCsrf(req("POST", { origin: base }), base);
    expect(rejected?.status).toBe(403);
    expect((await rejected!.json()).code).toBe("bff.csrf_header");
  });
});
