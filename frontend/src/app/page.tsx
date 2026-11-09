import Link from "next/link";

import { SignOutButton } from "@/components/SignOutButton";
import { getSession } from "@/lib/server-session";

export const dynamic = "force-dynamic";

export default async function Home() {
  const session = await getSession();

  return (
    <>
      <h1 className="text-3xl font-semibold tracking-tight">Kibootia</h1>
      {session ? (
        <div className="mt-6 space-y-6">
          <p data-testid="signed-in">You are signed in.</p>
          <p>
            <Link href="/me" className="font-medium text-blue-800 underline underline-offset-2 dark:text-blue-300">
              See who the API thinks you are
            </Link>
          </p>
          <SignOutButton />
        </div>
      ) : (
        <div className="mt-6 space-y-4">
          <p data-testid="signed-out">You are not signed in.</p>
          <a
            href="/auth/login"
            className="inline-block rounded-md bg-blue-800 px-4 py-2 text-sm font-medium text-white hover:bg-blue-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-800"
          >
            Sign in
          </a>
        </div>
      )}
    </>
  );
}
