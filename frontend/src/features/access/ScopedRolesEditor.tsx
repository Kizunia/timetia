"use client";

import { useId, useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { type ScopedRole, useAddScopedRole, useRemoveScopedRole, useRoles, useScopedRoles } from "@/features/access/api";
import { usePermissions } from "@/features/identity/permissions";
import { assignmentMessageFor } from "@/lib/api/problem";

const KIND_NAMES: Record<string, string> = {
  GLOBAL: "Everywhere",
  DEPARTMENT: "Department",
  DIVISION: "Division",
  BATCH: "Batch",
};

/** Where an assignment applies, in words: the kind and the code, or only the kind when the code is not known. */
function placeOf(role: ScopedRole): string {
  if (role.scopeType === "GLOBAL") return "Everywhere";
  const kind = KIND_NAMES[role.scopeType] ?? role.scopeType;
  return `${kind} ${role.scopeCode ?? role.scopeId ?? ""}`.trim();
}

/**
 * The roles one user holds in a department, division or batch, or everywhere (Faculty, Super HOD). An administrator adds
 * and removes them one at a time, so a screen that was open before another change cannot undo it. Everyone else who may
 * see roles sees the list. Hiding the controls is for usability only: the API lets only an administrator change them.
 */
export function ScopedRolesEditor({ userId }: { userId: string }) {
  const { me } = usePermissions();
  const roles = useRoles();
  const held = useScopedRoles(userId);
  const add = useAddScopedRole(userId);
  const remove = useRemoveScopedRole(userId);

  if (roles.isError) return <ErrorAlert error={roles.error} title="The roles could not be loaded" />;
  if (held.isError) return <ErrorAlert error={held.error} title="The user's roles in a place could not be loaded" />;
  if (!roles.data || !held.data) return <Skeleton className="h-24 w-full" aria-label="Loading roles in a place" />;

  const scopedRoles = roles.data.items.filter((role) => role.scopeType);
  const busy = add.isPending || remove.isPending;

  return (
    <div className="grid gap-4">
      {held.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">This user holds no roles in a place.</p>
      ) : (
        <Table>
          <TableCaption className="sr-only">Roles this user holds and where each applies</TableCaption>
          <TableHeader>
            <TableRow>
              <TableHead>Role</TableHead>
              <TableHead>Applies to</TableHead>
              {me.administrator && (
                <TableHead>
                  <span className="sr-only">Actions</span>
                </TableHead>
              )}
            </TableRow>
          </TableHeader>
          <TableBody>
            {held.data.map((role) => (
              <TableRow key={`${role.roleId}:${role.scopeId ?? ""}`} data-testid="scoped-role-row">
                <TableCell className="font-medium">{role.roleName}</TableCell>
                <TableCell>{placeOf(role)}</TableCell>
                {me.administrator && (
                  <TableCell className="text-right">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={busy}
                      aria-label={`Remove ${role.roleName}, ${placeOf(role)}`}
                      onClick={async () => {
                        try {
                          await remove.mutateAsync({ roleId: role.roleId, scopeId: role.scopeId });
                          toast.success("Role removed");
                        } catch {
                          // The alert below shows what went wrong.
                        }
                      }}
                    >
                      Remove
                    </Button>
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {remove.isError && <ErrorAlert error={remove.error} title="The role was not removed" />}

      {me.administrator &&
        (scopedRoles.length === 0 ? (
          <p className="text-sm text-muted-foreground">There are no roles that apply within a place.</p>
        ) : (
          <AddForm
            key={held.data.map((role) => `${role.roleId}:${role.scopeId ?? ""}`).join(",")}
            roles={scopedRoles.map((role) => ({ id: role.id, name: role.name, scopeType: role.scopeType! }))}
            busy={busy}
            onAdd={async (roleId, scopeCode) => {
              await add.mutateAsync({ roleId, scopeCode });
              toast.success("Role added");
            }}
            error={add.isError ? add.error : undefined}
          />
        ))}
    </div>
  );
}

function AddForm({
  roles,
  busy,
  onAdd,
  error,
}: {
  roles: { id: string; name: string; scopeType: string }[];
  busy: boolean;
  onAdd: (roleId: string, scopeCode: string | undefined) => Promise<void>;
  error: unknown;
}) {
  const [roleId, setRoleId] = useState(roles[0]!.id);
  const [code, setCode] = useState("");
  const roleField = useId();
  const codeField = useId();
  const codeHint = useId();
  const role = roles.find((candidate) => candidate.id === roleId) ?? roles[0]!;
  const global = role.scopeType === "GLOBAL";

  return (
    <form
      className="grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end"
      onSubmit={async (event) => {
        event.preventDefault();
        try {
          await onAdd(role.id, global ? undefined : code.trim());
          setCode("");
        } catch {
          // The alert below shows what went wrong.
        }
      }}
    >
      <div className="grid gap-1.5">
        <Label htmlFor={roleField}>Role</Label>
        <select
          id={roleField}
          value={role.id}
          onChange={(event) => setRoleId(event.target.value)}
          className="h-8 w-full rounded-lg border border-input bg-transparent px-2 text-sm focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
        >
          {roles.map((candidate) => (
            <option key={candidate.id} value={candidate.id}>
              {candidate.name}
            </option>
          ))}
        </select>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor={codeField}>{global ? "Applies to" : `${KIND_NAMES[role.scopeType] ?? role.scopeType} code`}</Label>
        <Input
          id={codeField}
          value={global ? "" : code}
          placeholder={global ? "Everywhere" : undefined}
          disabled={global || busy}
          required={!global}
          maxLength={100}
          autoComplete="off"
          aria-describedby={global ? undefined : codeHint}
          onChange={(event) => setCode(event.target.value)}
        />
        {!global && (
          <p id={codeHint} className="text-xs text-muted-foreground">
            The code people use for it, for example CSE or CSE-A. It must match exactly.
          </p>
        )}
      </div>
      <Button type="submit" disabled={busy || (!global && code.trim() === "")}>
        {busy ? "Adding…" : "Add role"}
      </Button>
      {error !== undefined && (
        <div className="sm:col-span-3">
          <ErrorAlert error={error} title="The role was not added" message={assignmentMessageFor(error)} />
        </div>
      )}
    </form>
  );
}
