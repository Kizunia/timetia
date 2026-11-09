"use client";

import { useEffect, useState } from "react";

import { ApiError, getMe, type Me } from "@/lib/api/me";

import { IdentityList } from "./IdentityList";

/** The same call as the server-rendered card, made from the browser through the `/api` proxy. */
export function MeViaProxy() {
  const [state, setState] = useState<{ me?: Me; error?: string }>({});

  useEffect(() => {
    let active = true;
    getMe("")
      .then((me) => active && setState({ me }))
      .catch((error: unknown) => {
        if (!active) return;
        setState({ error: error instanceof ApiError && error.status === 401 ? "Your session has ended. Sign in again." : "The request failed." });
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
