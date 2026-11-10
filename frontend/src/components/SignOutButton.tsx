"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
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
      <Button type="button" variant="outline" onClick={signOut} disabled={busy}>
        {busy ? "Signing out…" : "Sign out"}
      </Button>
      {failed && (
        <p role="alert" className="mt-2 text-sm text-destructive">
          Sign-out failed. Try again.
        </p>
      )}
    </div>
  );
}
