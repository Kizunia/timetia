"use client";

import { useEffect, useState } from "react";

import { browserApi, type Me, unwrap } from "@/lib/api/client";
import { ApiError, messageFor } from "@/lib/api/problem";

import { IdentityList } from "./IdentityList";

/** The same call as the server-rendered card, made from the browser through the `/api` proxy. */
export function MeViaProxy() {
  const [state, setState] = useState<{ me?: Me; error?: string }>({});

  useEffect(() => {
    let active = true;
    browserApi()
      .GET("/api/v1/me")
      .then((result) => active && setState({ me: unwrap(result) }))
      .catch((error: unknown) => {
        if (!active) return;
        // A 401 may come from the proxy without a problem body; it still means the session ended.
        const ended = error instanceof ApiError && error.status === 401;
        setState({ error: ended ? "Your session has ended. Sign in again." : messageFor(error) });
      });
    return () => {
      active = false;
    };
  }, []);

  if (state.error) {
    return (
      <p role="alert" className="text-red-700 dark:text-red-400">
        {state.error}
      </p>
    );
  }
  if (!state.me) return <p aria-live="polite">Loading…</p>;
  return <IdentityList me={state.me} testId="me-browser" />;
}
