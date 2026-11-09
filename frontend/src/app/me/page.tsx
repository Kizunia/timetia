import Link from "next/link";
import { redirect } from "next/navigation";

import { IdentityList } from "@/components/IdentityList";
import { MeViaProxy } from "@/components/MeViaProxy";
import { SignOutButton } from "@/components/SignOutButton";
import { ApiError, getMe, type Me } from "@/lib/api/me";
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
    serverMe = await getMe(config().apiBaseUrl, { headers: { Authorization: `Bearer ${session.accessToken}` } });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect("/auth/login?returnTo=/me");
    serverError = "The API could not be reached.";
  }

  return (
    <>
      <h1 className="text-3xl font-semibold tracking-tight">Who am I</h1>

      <section aria-labelledby="server-heading" className="mt-8">
        <h2 id="server-heading" className="mb-3 text-lg font-medium">
          Rendered on the server
        </h2>
        {serverMe ? (
          <IdentityList me={serverMe} testId="me-server" />
        ) : (
          <p role="alert" className="text-red-700 dark:text-red-400">
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

      <div className="mt-10 flex items-center gap-6">
        <Link href="/" className="font-medium text-blue-800 underline underline-offset-2 dark:text-blue-300">
          Home
        </Link>
        <SignOutButton />
      </div>
    </>
  );
}
