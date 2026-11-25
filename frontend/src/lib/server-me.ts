import "server-only";

import { cache } from "react";
import { redirect } from "next/navigation";

import { type Me, serverApi, unwrap } from "@/lib/api/client";
import { ApiError } from "@/lib/api/problem";
import { config } from "@/lib/config";
import { getSession } from "@/lib/server-session";

/**
 * The signed-in user as the API reports them, for server components: the layout and the pages of one request share the
 * one call. Without a session it sends the visitor to sign in and, for a user an administrator disabled or deleted, to the
 * page that says so. Returns null only when there is no session and {@code optional} is true, as on the home page.
 */
export const getMe = cache(async (optional = false): Promise<Me | null> => {
  const session = await getSession();
  if (!session) {
    if (optional) return null;
    redirect("/auth/login");
  }
  try {
    return unwrap(await serverApi(config().apiBaseUrl, session.accessToken).GET("/api/v1/me"));
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) {
        if (optional) return null;
        redirect("/auth/login");
      }
      if (error.code === "identity.account_disabled") redirect("/account-disabled");
    }
    throw error;
  }
});
