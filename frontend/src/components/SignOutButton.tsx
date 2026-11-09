"use client";

import { useState } from "react";

import { CSRF_HEADER, CSRF_HEADER_VALUE } from "@/lib/csrf";

/** Ends the local session, then sends the browser to Keycloak to end its session too. */
export function SignOutButton() {
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);

  async function signOut() {
    setBusy(true);
    setFailed(false);
    try {
      const response = await fetch("/auth/logout", { method: "POST", headers: { [CSRF_HEADER]: CSRF_HEADER_VALUE } });
      if (!response.ok) throw new Error(`sign-out answered ${response.status}`);
      const { redirectTo } = (await response.json()) as { redirectTo: string };
      window.location.assign(redirectTo);
    } catch {
      setBusy(false);
      setFailed(true);
    }
  }

  return (
    <div>
      <button
        type="button"
        onClick={signOut}
        disabled={busy}
        className="rounded-md border border-neutral-300 px-4 py-2 text-sm font-medium hover:bg-neutral-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700 disabled:opacity-60 dark:border-neutral-600 dark:hover:bg-neutral-800"
      >
        {busy ? "Signing out…" : "Sign out"}
      </button>
      {failed && (
        <p role="alert" className="mt-2 text-sm text-red-700 dark:text-red-400">
          Sign-out failed. Try again.
        </p>
      )}
    </div>
  );
}
