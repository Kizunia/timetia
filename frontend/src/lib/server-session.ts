import "server-only";

import { cookies } from "next/headers";

import { config } from "./config";
import { readSession, type Session } from "./session";

/** The signed-in user's session for Server Components, or null. Refreshing happens earlier, in `proxy.ts`. */
export async function getSession(): Promise<Session | null> {
  const store = await cookies();
  return readSession(store.toString(), config().sessionSecret);
}
