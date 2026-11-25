"use client";

import Link from "next/link";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useRoles } from "@/features/access/api";
import { Can } from "@/features/identity/permissions";

/** The list of roles. Needs access.roles.view; creating one needs access.roles.manage. The API enforces both. */
export function RolesScreen() {
  const roles = useRoles();

  return (
    <section aria-labelledby="roles-heading">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 id="roles-heading" className="text-2xl font-semibold tracking-tight">
          Roles
        </h1>
        <Can permission="access.roles.manage">
          <Link href="/admin/roles/new" className={buttonVariants()}>
            New role
          </Link>
        </Can>
      </div>
      <p className="mt-2 max-w-prose text-sm text-muted-foreground">
        A role is a bundle of permissions. Someone can do what the roles they hold allow, and a change applies on their next
        request.
      </p>

      <div className="mt-6">
        {roles.isError && <ErrorAlert error={roles.error} title="The roles could not be loaded" />}
        {roles.isPending && <Skeleton className="h-32 w-full" aria-label="Loading roles" />}
        {roles.data && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Description</TableHead>
                <TableHead className="text-right">Permissions</TableHead>
                <TableHead className="text-right">Users</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {roles.data.items.map((role) => (
                <TableRow key={role.id} data-testid="role-row">
                  <TableCell>
                    <Link href={`/admin/roles/${role.id}`} className="font-medium text-primary underline underline-offset-2">
                      {role.name}
                    </Link>{" "}
                    {role.builtIn && <Badge variant="secondary">built in</Badge>}
                  </TableCell>
                  <TableCell className="text-muted-foreground">{role.description}</TableCell>
                  <TableCell className="text-right">{role.permissions.length}</TableCell>
                  <TableCell className="text-right">{role.assignedUsers}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>
    </section>
  );
}
