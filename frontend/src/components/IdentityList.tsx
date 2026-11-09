import type { Me } from "@/lib/api/me";

/** Shared by the server-rendered card and the browser card, so both show exactly the same fields. */
export function IdentityList({ me, testId }: { me: Me; testId: string }) {
  return (
    <dl data-testid={testId} className="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1">
      <dt className="text-neutral-600 dark:text-neutral-400">Username</dt>
      <dd data-field="username">{me.username}</dd>
      <dt className="text-neutral-600 dark:text-neutral-400">Name</dt>
      <dd data-field="name">{me.name}</dd>
      <dt className="text-neutral-600 dark:text-neutral-400">Email</dt>
      <dd data-field="email">{me.email}</dd>
      <dt className="text-neutral-600 dark:text-neutral-400">Email verified</dt>
      <dd data-field="emailVerified">{me.emailVerified ? "yes" : "no"}</dd>
      <dt className="text-neutral-600 dark:text-neutral-400">User ID</dt>
      <dd data-field="id" className="break-all font-mono text-sm">
        {me.id}
      </dd>
    </dl>
  );
}
