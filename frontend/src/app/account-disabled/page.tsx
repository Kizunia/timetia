import { SignOutButton } from "@/components/SignOutButton";

/**
 * Where a user lands when an administrator has disabled or deleted their account (ADR 0041). They stay signed in at the
 * web application, but the API refuses every call, so this page says why instead of an error on each screen.
 */
export default function AccountDisabledPage() {
  return (
    <main id="main" className="mx-auto max-w-lg px-4 py-24 sm:px-6">
      <h1 className="text-2xl font-semibold tracking-tight">Your account is disabled</h1>
      <p className="mt-3 text-muted-foreground">
        An administrator turned off your access. If you think this is a mistake, ask them to turn it back on.
      </p>
      <div className="mt-6">
        <SignOutButton />
      </div>
    </main>
  );
}
