import Link from "next/link";
import { redirect } from "next/navigation";

import { PageShell } from "@/components/PageShell";
import { IdentityList } from "@/components/IdentityList";
import { MeViaProxy } from "@/components/MeViaProxy";
import { type Me, serverApi, unwrap } from "@/lib/api/client";
import { ApiError } from "@/lib/api/problem";
import { config } from "@/lib/config";
import { getSession } from "@/lib/server-session";

export const dynamic = "force-dynamic";

export default async function MePage() {
  const session = await getSession();
  // proxy.ts already sends signed-out visitors to sign in; this is the second line of defence.
  if (!session) redirect("/auth/login?returnTo=/me");

  // Server-side call: straight to Spring with the token, no browser involved.
  let serverMe: Me | null = null;
  let serverError: string | null = null;
  try {
    serverMe = unwrap(await serverApi(config().apiBaseUrl, session.accessToken).GET("/api/v1/me"));
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect("/auth/login?returnTo=/me");
    if (error instanceof ApiError && error.code === "identity.account_disabled") redirect("/account-disabled");
    serverError = "The API could not be reached.";
  }

  return (
    <PageShell>
      <h1 className="text-3xl font-semibold tracking-tight">Who am I</h1>

      <section aria-labelledby="server-heading" className="mt-8">
        <h2 id="server-heading" className="mb-3 text-lg font-medium">
          Rendered on the server
        </h2>
        {serverMe ? (
          <IdentityList me={serverMe} testId="me-server" />
        ) : (
          <p role="alert" className="text-destructive">
            {serverError}
          </p>
        )}
      </section>

      <section aria-labelledby="browser-heading" className="mt-8">
        <h2 id="browser-heading" className="mb-3 text-lg font-medium">
          Fetched by the browser through the proxy
        </h2>
        <MeViaProxy />
      </section>

      <div className="mt-10">
        <Link href="/" className="font-medium text-primary underline underline-offset-2">
          Home
        </Link>
      </div>
    </PageShell>
  );
}
