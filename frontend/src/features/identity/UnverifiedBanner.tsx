"use client";

import { useMutation } from "@tanstack/react-query";
import { MailWarningIcon } from "lucide-react";
import { toast } from "sonner";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { browserApi, unwrap } from "@/lib/api/client";
import { messageFor } from "@/lib/api/problem";
import { CSRF_HEADER, CSRF_HEADER_VALUE } from "@/lib/csrf";
import { reloadPage } from "@/lib/reload";

/**
 * Shown to a signed-in user whose email address is not verified (ADR 0041). Until it is, the API lets them view their
 * own profile, ask for the email again and sign out, and refuses everything else. Following the link in the email
 * verifies the address at once at Keycloak, but the access token still says otherwise until it is refreshed, so the
 * second button refreshes it now instead of making the user wait.
 */
export function UnverifiedBanner() {
  const resend = useMutation({
    mutationFn: async () => unwrap(await browserApi().POST("/api/v1/me/verification-email")),
    onSuccess: () => toast.success("We sent you another email. Follow the link in it."),
    onError: (error) => toast.error(messageFor(error)),
  });

  const refresh = useMutation({
    mutationFn: async () => {
      const response = await fetch("/auth/refresh", { method: "POST", headers: { [CSRF_HEADER]: CSRF_HEADER_VALUE } });
      if (!response.ok) throw new Error(`refresh answered ${response.status}`);
    },
    onSuccess: () => reloadPage(),
    onError: () => toast.error("Your session could not be refreshed. Sign out and sign in again."),
  });

  return (
    <Alert role="status" data-testid="unverified-banner" className="mx-auto mt-4 max-w-3xl">
      <MailWarningIcon aria-hidden="true" />
      <AlertTitle>Verify your email address</AlertTitle>
      <AlertDescription>
        <p>
          Until you do, you can see your own profile and sign out, and nothing else. We sent a link to the address you
          signed up with.
        </p>
        <div className="mt-3 flex flex-wrap gap-2">
          <Button type="button" variant="outline" size="sm" disabled={resend.isPending} onClick={() => resend.mutate()}>
            Send the email again
          </Button>
          <Button type="button" size="sm" disabled={refresh.isPending} onClick={() => refresh.mutate()}>
            I have verified
          </Button>
        </div>
      </AlertDescription>
    </Alert>
  );
}
