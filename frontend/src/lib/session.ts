import { createSecretKey, hkdfSync } from "node:crypto";

import { EncryptJWT, jwtDecrypt } from "jose";

import { expireCookie, parseCookies, serializeCookie } from "./cookies";

/**
 * The signed-in user's session: tokens held on the server side of the browser boundary, in an encrypted HttpOnly
 * cookie that is split into chunks when it is too large for one cookie (ADR 0026). The browser never sees a token.
 */
export interface Session {
  accessToken: string;
  refreshToken: string;
  idToken: string;
  /** Epoch seconds at which the access token expires. */
  accessExpiresAt: number;
  /** Epoch seconds after which the session ends, however often tokens are refreshed. */
  sessionExpiresAt: number;
}

export const SESSION_COOKIE = "timetia_session";
export const LOGIN_COOKIE = "timetia_login";

/** Below the 4096-byte cookie limit once the name and attributes are added. */
export const CHUNK_SIZE = 3600;
const MAX_CHUNKS = 8;
const SESSION_MAX_AGE_SECONDS = 10 * 60 * 60;

function key(secret: string) {
  // HKDF keeps the cookie key independent of the raw secret and gives the 32 bytes A256GCM needs.
  return createSecretKey(Buffer.from(hkdfSync("sha256", secret, "", "timetia-session-cookie", 32)));
}

export async function seal(payload: Record<string, unknown>, secret: string, ttlSeconds: number): Promise<string> {
  return new EncryptJWT(payload)
    .setProtectedHeader({ alg: "dir", enc: "A256GCM" })
    .setIssuedAt()
    .setExpirationTime(`${ttlSeconds}s`)
    .encrypt(key(secret));
}

export async function unseal<T>(token: string, secret: string): Promise<T | null> {
  try {
    const { payload } = await jwtDecrypt(token, key(secret), { keyManagementAlgorithms: ["dir"], contentEncryptionAlgorithms: ["A256GCM"] });
    return payload as T;
  } catch {
    // Tampered, expired or sealed with another key: treat all of these as "no session".
    return null;
  }
}

export function newSession(tokens: Pick<Session, "accessToken" | "refreshToken" | "idToken" | "accessExpiresAt">, now = Date.now()): Session {
  return { ...tokens, sessionExpiresAt: Math.floor(now / 1000) + SESSION_MAX_AGE_SECONDS };
}

export async function readSession(cookieHeader: string | null | undefined, secret: string): Promise<Session | null> {
  const cookies = parseCookies(cookieHeader);
  let sealed = "";
  for (let index = 0; index < MAX_CHUNKS; index++) {
    const chunk = cookies.get(`${SESSION_COOKIE}.${index}`);
    if (chunk === undefined) break;
    sealed += chunk;
  }
  if (!sealed) return null;
  const session = await unseal<Session>(sealed, secret);
  if (!session?.accessToken || !session.refreshToken) return null;
  if (session.sessionExpiresAt * 1000 <= Date.now()) return null;
  return session;
}

/** Set-Cookie values that store the session and remove chunks left over from a previously larger one. */
export async function writeSession(session: Session, secret: string, secure: boolean, existingCookieHeader?: string | null): Promise<string[]> {
  const ttl = Math.max(1, session.sessionExpiresAt - Math.floor(Date.now() / 1000));
  const sealed = await seal({ ...session }, secret, ttl);
  const chunks: string[] = [];
  for (let offset = 0; offset < sealed.length; offset += CHUNK_SIZE) chunks.push(sealed.slice(offset, offset + CHUNK_SIZE));
  if (chunks.length > MAX_CHUNKS) throw new Error("The session is too large to store in cookies");
  const cookies = chunks.map((chunk, index) => serializeCookie(`${SESSION_COOKIE}.${index}`, chunk, { maxAge: ttl, secure }));
  for (const name of parseCookies(existingCookieHeader).keys()) {
    if (name.startsWith(`${SESSION_COOKIE}.`) && Number(name.slice(SESSION_COOKIE.length + 1)) >= chunks.length) {
      cookies.push(expireCookie(name, secure));
    }
  }
  return cookies;
}

export function clearSessionCookies(existingCookieHeader: string | null | undefined, secure: boolean): string[] {
  const names = [...parseCookies(existingCookieHeader).keys()].filter((name) => name.startsWith(`${SESSION_COOKIE}.`));
  return names.map((name) => expireCookie(name, secure));
}
