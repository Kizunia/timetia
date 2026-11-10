"use client";

import Link from "next/link";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { UserRolesEditor } from "@/features/access/UserRolesEditor";
import { type UserAction, useUser, useUserAction } from "@/features/identity/api";
import { usePermissions } from "@/features/identity/permissions";
import { UserActions } from "@/features/identity/UserActions";
import { UserAvatar } from "@/features/identity/UserAvatar";
import { StateBadge } from "@/features/identity/UsersScreen";

const DONE: Record<UserAction, string> = {
  disable: "User disabled",
  enable: "User enabled",
  delete: "User deleted",
  restore: "User restored",
  erase: "Erasing the user has begun",
  "resend-invitation": "Invitation sent again",
};

/** One user: who they are, what an administrator can do to them, and which roles they hold. */
export function UserDetailScreen({ id }: { id: string }) {
  const { me, has } = usePermissions();
  const user = useUser(id);
  const act = useUserAction(id);

  return (
    <section aria-labelledby="user-heading" className="grid gap-6">
      <p>
        <Link href="/admin/users" className="text-sm font-medium text-primary underline underline-offset-2">
          ← All users
        </Link>
      </p>

      {user.isError && <ErrorAlert error={user.error} title="The user could not be loaded" />}
      {user.isPending && <Skeleton className="h-40 w-full" aria-label="Loading the user" />}

      {user.data && (
        <>
          <div className="flex flex-wrap items-center gap-3">
            <UserAvatar
              userId={user.data.id}
              name={user.data.name}
              version={me.avatarsEnabled ? user.data.avatarVersion : null}
              size="large"
            />
            <h1 id="user-heading" className="text-2xl font-semibold tracking-tight">
              {user.data.name}
            </h1>
            <StateBadge state={user.data.state} />
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Account</CardTitle>
              <CardDescription>What Keycloak says about them, as of their last sign-in.</CardDescription>
            </CardHeader>
            <CardContent>
              <dl className="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1">
                <dt className="text-muted-foreground">Email</dt>
                <dd data-field="email">
                  {user.data.email}{" "}
                  <Badge variant={user.data.emailVerified ? "outline" : "secondary"}>
                    {user.data.emailVerified ? "verified" : "not verified"}
                  </Badge>
                </dd>
                <dt className="text-muted-foreground">Username</dt>
                <dd data-field="username">{user.data.username}</dd>
                <dt className="text-muted-foreground">User ID</dt>
                <dd className="break-all font-mono text-sm">{user.data.id}</dd>
                <dt className="text-muted-foreground">Created</dt>
                <dd>
                  <time dateTime={user.data.createdAt}>{new Date(user.data.createdAt).toLocaleString()}</time>
                </dd>
              </dl>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Actions</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3">
              <UserActions
                user={user.data}
                selfId={me.id}
                can={has}
                busy={act.isPending}
                onAction={(action) => act.mutate(action, { onSuccess: () => toast.success(DONE[action]) })}
              />
              {act.isError && <ErrorAlert error={act.error} />}
            </CardContent>
          </Card>

          {has("access.roles.view") && user.data.state !== "DELETED" && (
            <Card>
              <CardHeader>
                <CardTitle>Roles</CardTitle>
                <CardDescription>What a user may do comes from the roles they hold. A change applies on their next request.</CardDescription>
              </CardHeader>
              <CardContent>
                <UserRolesEditor userId={user.data.id} />
              </CardContent>
            </Card>
          )}
        </>
      )}
    </section>
  );
}
