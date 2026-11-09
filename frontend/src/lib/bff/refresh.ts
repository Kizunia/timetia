import { logger } from "../logger";
import { clearSessionCookies, readSession, writeSession, type Session } from "../session";
import { isSecure, type Deps } from "./deps";

/** Refresh the access token this many seconds before it expires, so a request never carries one about to lapse. */
const REFRESH_MARGIN_SECONDS = 30;

export type SessionResult =
  | { status: "none"; setCookies: string[] }
  /** `setCookies` is non-empty only when the tokens were just refreshed and must be stored. */
  | { status: "ok"; session: Session; setCookies: string[] }
  /** The refresh token was rejected or Keycloak could not be reached: the user must sign in again. */
  | { status: "refresh_failed"; setCookies: string[] };

/** One refresh per refresh token at a time, so concurrent requests do not each spend (and invalidate) the same token. */
const inFlight = new Map<string, Promise<Session>>();

/**
 * Reads the session from the request and refreshes the access token when it is close to expiry (single-flight per
 * refresh token). Never throws: a failed refresh is reported so the caller can force a new sign-in.
 */
export async function currentSession(cookieHeader: string | null, deps: Deps): Promise<SessionResult> {
  const { config } = deps;
  const secure = isSecure(config);
  const session = await readSession(cookieHeader, config.sessionSecret);
  if (!session) {
    // An unreadable session (tampered, expired or from an old key) is cleared rather than left to fail every request.
    return { status: "none", setCookies: clearSessionCookies(cookieHeader, secure) };
  }
  const nowSeconds = Math.floor(deps.now() / 1000);
  if (session.accessExpiresAt - nowSeconds > REFRESH_MARGIN_SECONDS) {
    return { status: "ok", session, setCookies: [] };
  }

  try {
    let refreshing = inFlight.get(session.refreshToken);
    if (!refreshing) {
      refreshing = refreshSession(session, deps).finally(() => inFlight.delete(session.refreshToken));
      inFlight.set(session.refreshToken, refreshing);
    }
    const refreshed = await refreshing;
    return { status: "ok", session: refreshed, setCookies: await writeSession(refreshed, config.sessionSecret, secure, cookieHeader) };
  } catch (error) {
    // Only the kind of failure is logged; the error can echo request details.
    logger.warn({ reason: error instanceof Error ? error.name : "unknown" }, "session refresh failed; sign-in required");
    return { status: "refresh_failed", setCookies: clearSessionCookies(cookieHeader, secure) };
  }
}

async function refreshSession(session: Session, deps: Deps): Promise<Session> {
  const tokens = await deps.oidc.refresh(session.refreshToken);
  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    idToken: tokens.idToken,
    accessExpiresAt: Math.floor(deps.now() / 1000) + tokens.expiresIn,
    // Refreshing keeps the user signed in but never extends the session beyond its original limit.
    sessionExpiresAt: session.sessionExpiresAt,
  };
}
