"use client";

import Link from "next/link";
import { useState } from "react";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Pager } from "@/components/Pager";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { InviteDialog } from "@/features/identity/InviteDialog";
import { Can, usePermissions } from "@/features/identity/permissions";
import { UserAvatar } from "@/features/identity/UserAvatar";
import { useUsers, type UserState } from "@/features/identity/api";

const STATES: { value: "ALL" | UserState; label: string }[] = [
  { value: "ALL", label: "All users" },
  { value: "ACTIVE", label: "Active" },
  { value: "INVITED", label: "Invited" },
  { value: "DISABLED", label: "Disabled" },
  { value: "DELETED", label: "Deleted" },
];

/** How a state looks. Colour is never the only signal: the word is always there. */
export function StateBadge({ state }: { state: UserState }) {
  const variant = state === "ACTIVE" ? "default" : state === "DELETED" ? "destructive" : "secondary";
  return <Badge variant={variant}>{state.charAt(0) + state.slice(1).toLowerCase()}</Badge>;
}

/** The list of users, with search, a filter by state and paging. Needs identity.profiles.view; the API enforces it. */
export function UsersScreen() {
  const { me } = usePermissions();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [state, setState] = useState<"ALL" | UserState>("ALL");
  const users = useUsers({ page, size: 20, q: q || undefined, state: state === "ALL" ? undefined : state, sort: "name,asc" });

  return (
    <section aria-labelledby="users-heading">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 id="users-heading" className="text-2xl font-semibold tracking-tight">
          Users
        </h1>
        <Can permission="identity.users.invite">
          <InviteDialog />
        </Can>
      </div>

      <form
        role="search"
        className="mt-6 flex flex-wrap items-end gap-3"
        onSubmit={(event) => {
          event.preventDefault();
          setQ(search.trim());
          setPage(0);
        }}
      >
        <div className="grid gap-1.5">
          <Label htmlFor="user-search">Search</Label>
          <Input id="user-search" type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Name, username or email" className="w-72" />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="user-state">Show</Label>
          <Select
            value={state}
            onValueChange={(value) => {
              setState(value as "ALL" | UserState);
              setPage(0);
            }}
          >
            <SelectTrigger id="user-state" className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {STATES.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button type="submit" variant="secondary">
          Search
        </Button>
      </form>

      <div className="mt-6">
        {users.isError && <ErrorAlert error={users.error} title="The users could not be loaded" />}
        {users.isPending && (
          <div className="grid gap-2" aria-busy="true" aria-label="Loading users">
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
          </div>
        )}
        {users.data && (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>State</TableHead>
                  <TableHead>Roles</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.data.items.map((user) => (
                  <TableRow key={user.id} data-testid="user-row">
                    <TableCell>
                      <div className="flex items-center gap-3">
                        <UserAvatar userId={user.id} name={user.name} version={me.avatarsEnabled ? user.avatarVersion : null} />
                        <Link href={`/admin/users/${user.id}`} className="font-medium text-primary underline underline-offset-2">
                          {user.name}
                        </Link>
                      </div>
                    </TableCell>
                    <TableCell>{user.email}</TableCell>
                    <TableCell>
                      <StateBadge state={user.state} />
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {user.roles.map((role) => (
                          <Badge key={role.id} variant="outline">
                            {role.name}
                          </Badge>
                        ))}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
                {users.data.items.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} className="text-center text-muted-foreground">
                      No users match.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
            <Pager
              page={users.data.page.number}
              totalPages={users.data.page.totalPages}
              totalElements={users.data.page.totalElements}
              onPage={setPage}
            />
          </>
        )}
      </div>
    </section>
  );
}
