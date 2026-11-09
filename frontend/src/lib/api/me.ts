import { CSRF_HEADER, CSRF_HEADER_VALUE } from "../csrf";

/**
 * TEMPORARY, hand-written client for `GET /api/v1/me` (milestone 1 exception).
 *
 * The frontend rule is a client generated from the committed `openapi.json` and never hand-written request types
 * (ADR 0038). The OpenAPI description and its drift check arrive in milestone 2, which deletes this file and generates
 * the client. Keep it to this one call.
 */
export interface Me {
  id: string;
  username: string | null;
  email: string | null;
  emailVerified: boolean;
  name: string | null;
}

export class ApiError extends Error {
  constructor(readonly status: number) {
    super(`The API answered ${status}`);
    this.name = "ApiError";
  }
}

/**
 * @param baseUrl the Spring API from a server (`http://localhost:8080`), or "" in the browser to use the `/api` proxy
 * @param init   an `Authorization` header from a server, nothing in the browser (the proxy adds the token)
 */
export async function getMe(baseUrl: string, init: RequestInit = {}): Promise<Me> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  // Harmless on a server-to-server call, and required by the proxy for anything but GET; sent for uniformity.
  headers.set(CSRF_HEADER, CSRF_HEADER_VALUE);
  const response = await fetch(`${baseUrl}/api/v1/me`, { ...init, headers, cache: "no-store" });
  if (!response.ok) throw new ApiError(response.status);
  return (await response.json()) as Me;
}
