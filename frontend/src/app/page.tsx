import Link from "next/link";
import { redirect } from "next/navigation";

import { PageShell } from "@/components/PageShell";
import { getMe } from "@/lib/server-me";

export const dynamic = "force-dynamic";

/** Holding any of these means the person has a role in Timetia and so has a timetable to read. */
const TIMETABLE_PERMISSIONS = ["timetable.live.view", "timetable.live.view-own", "timetable.live.view-all"];

export default async function Home() {
  const me = await getMe(true);
  if (me?.permissions.some((permission) => TIMETABLE_PERMISSIONS.includes(permission))) redirect("/timetable");

  return (
    <PageShell signedInOnly={false}>
      <h1 className="text-3xl font-semibold tracking-tight">Timetia</h1>
      {me ? (
        <div className="mt-6 space-y-3">
          <p data-testid="signed-in">You are signed in.</p>
          <p>You have no role in Timetia yet. Ask an administrator.</p>
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
