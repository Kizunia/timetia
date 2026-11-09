import createClient from "openapi-fetch";

import { CSRF_HEADER, CSRF_HEADER_VALUE } from "../csrf";
import { ApiError, isProblem } from "./problem";
import type { components, paths } from "./schema.gen";

/**
 * The typed API client (ADR 0038, ADR 0043). Request and response types come from `schema.gen.ts`, which is generated
 * from the backend's committed `openapi.json` (`pnpm api:generate`); never write request types by hand.
 */
export type Api = ReturnType<typeof createClient<paths>>;

/** The signed-in user, as `GET /api/v1/me` returns it. */
export type Me = components["schemas"]["MeResponse"];

type FetchLike = (request: Request) => Promise<Response>;

const noStore: FetchLike = (request) => fetch(request, { cache: "no-store" });

/**
 * For code running on the Next.js server: calls Spring directly with the access token from the session.
 *
 * @param baseUrl the Spring API, for example `http://localhost:8080`
 */
export function serverApi(baseUrl: string, accessToken: string, fetchImpl: FetchLike = noStore): Api {
  return createClient<paths>({
    baseUrl,
    fetch: fetchImpl,
    // The CSRF header is harmless on a server-to-server call and is what the proxy requires for anything but GET.
    headers: { Authorization: `Bearer ${accessToken}`, [CSRF_HEADER]: CSRF_HEADER_VALUE },
  });
}

/**
 * For code running in the browser: calls the same-origin `/api` proxy, which adds the token from the session cookie and
 * checks the CSRF header (ADR 0042).
 */
export function browserApi(origin: string = "", fetchImpl: FetchLike = noStore): Api {
  return createClient<paths>({ baseUrl: origin, fetch: fetchImpl, headers: { [CSRF_HEADER]: CSRF_HEADER_VALUE } });
}

/** What openapi-fetch returns for a call: the parsed body on success, or the parsed error body on failure. */
interface Result<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

/** The data of a successful call, or an {@link ApiError} carrying the status and, when there is one, the problem. */
export function unwrap<T>(result: Result<T>): T {
  if (result.error !== undefined || !result.response.ok) {
    throw new ApiError(result.response.status, isProblem(result.error) ? result.error : undefined);
  }
  return result.data as T;
}
