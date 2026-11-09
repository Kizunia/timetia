import { checkCsrf } from "../csrf";
import { expireCookie, parseCookies, serializeCookie } from "../cookies";
import { logger } from "../logger";
import { problem } from "../problem";
import { LOGIN_COOKIE, newSession, readSession, seal, unseal, writeSession, clearSessionCookies } from "../session";
import { isSecure, type Deps } from "./deps";

const LOGIN_TTL_SECONDS = 600;

interface LoginState {
  state: string;
  nonce: string;
  codeVerifier: string;
  returnTo: string;
}

/** Only same-site paths: a `returnTo` pointing elsewhere would turn sign-in into an open redirect. */
export function safeReturnTo(value: string | null): string {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.includes("\\") || /[\u0000-\u001f]/.test(value)) {
    return "/";
  }
  return value;
}

function redirect(location: string, setCookies: string[]): Response {
  const headers = new Headers({ Location: location, "Cache-Control": "no-store" });
  for (const cookie of setCookies) headers.append("Set-Cookie", cookie);
  return new Response(null, { status: 302, headers });
}

/** GET /auth/login: starts Authorization Code with PKCE. */
export async function login(request: Request, deps: Deps): Promise<Response> {
  const { config } = deps;
  const returnTo = safeReturnTo(new URL(request.url).searchParams.get("returnTo"));
  const attempt = await deps.oidc.createLogin(`${config.publicBaseUrl}/auth/callback`);
  const sealed = await seal(
    { state: attempt.state, nonce: attempt.nonce, codeVerifier: attempt.codeVerifier, returnTo } satisfies LoginState,
    config.sessionSecret,
    LOGIN_TTL_SECONDS,
  );
  return redirect(attempt.url, [serializeCookie(LOGIN_COOKIE, sealed, { maxAge: LOGIN_TTL_SECONDS, secure: isSecure(config) })]);
}

/** GET /auth/callback: checks state, nonce and the PKCE verifier, exchanges the code and stores the session. */
export async function callback(request: Request, deps: Deps): Promise<Response> {
  const { config } = deps;
  const secure = isSecure(config);
  const cookieHeader = request.headers.get("cookie");
  const clearLogin = expireCookie(LOGIN_COOKIE, secure);

  const sealed = parseCookies(cookieHeader).get(LOGIN_COOKIE);
  const attempt = sealed ? await unseal<LoginState>(sealed, config.sessionSecret) : null;
  if (!attempt) {
    return problem(400, "bff.login_expired", "The sign-in attempt expired or did not start here. Start again.", {
      "Set-Cookie": clearLogin,
    });
  }

  // Behind a proxy the request URL may be an internal one; the redirect URI Keycloak was given is the public one.
  const incoming = new URL(request.url);
  const currentUrl = new URL(`${config.publicBaseUrl}${incoming.pathname}${incoming.search}`);

  try {
    const tokens = await deps.oidc.exchangeCode(currentUrl, {
      state: attempt.state,
      nonce: attempt.nonce,
      codeVerifier: attempt.codeVerifier,
    });
    const session = newSession(
      {
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        idToken: tokens.idToken,
        accessExpiresAt: Math.floor(deps.now() / 1000) + tokens.expiresIn,
      },
      deps.now(),
    );
    const cookies = await writeSession(session, config.sessionSecret, secure, cookieHeader);
    cookies.push(clearLogin);
    logger.info("user signed in");
    return redirect(`${config.publicBaseUrl}${safeReturnTo(attempt.returnTo)}`, cookies);
  } catch (error) {
    // State, nonce, PKCE and provider errors all end here. The cause is logged by kind only: it can echo the code.
    logger.warn({ reason: error instanceof Error ? error.name : "unknown" }, "sign-in callback rejected");
    return problem(400, "bff.login_failed", "Sign-in could not be completed. Start again.", { "Set-Cookie": clearLogin });
  }
}

/**
 * POST /auth/logout: ends the local session and returns where the browser must go to end the Keycloak session too.
 * It is a state-changing request, so the CSRF rule applies; the sign-out button calls it with `fetch`.
 */
export async function logout(request: Request, deps: Deps): Promise<Response> {
  const { config } = deps;
  const rejected = checkCsrf(request, config.publicBaseUrl);
  if (rejected) return rejected;

  const cookieHeader = request.headers.get("cookie");
  const session = await readSession(cookieHeader, config.sessionSecret);
  const headers = new Headers({ "Content-Type": "application/json", "Cache-Control": "no-store" });
  for (const cookie of clearSessionCookies(cookieHeader, isSecure(config))) headers.append("Set-Cookie", cookie);
  logger.info("user signed out");
  return new Response(JSON.stringify({ redirectTo: deps.oidc.endSessionUrl(session?.idToken) }), { status: 200, headers });
}
