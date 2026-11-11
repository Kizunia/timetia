import Link from "next/link";

import { PageShell } from "@/components/PageShell";
import { getMe } from "@/lib/server-me";

export const dynamic = "force-dynamic";

export default async function Home() {
  const me = await getMe(true);

  return (
    <PageShell signedInOnly={false}>
      <h1 className="text-3xl font-semibold tracking-tight">Timetia</h1>
      {me ? (
        <div className="mt-6 space-y-3">
          <p data-testid="signed-in">You are signed in.</p>
          <p>
            <Link href="/profile" className="font-medium text-primary underline underline-offset-2">
              Your profile
            </Link>
          </p>
          <p>
            <Link href="/me" className="font-medium text-primary underline underline-offset-2">
              See who the API thinks you are
            </Link>
          </p>
        </div>
      ) : (
        <div className="mt-6 space-y-4">
          <p data-testid="signed-out">You are not signed in.</p>
          <a
            href="/auth/login"
            className="inline-block rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
          >
            Sign in
          </a>
        </div>
      )}
    </PageShell>
  );
}
