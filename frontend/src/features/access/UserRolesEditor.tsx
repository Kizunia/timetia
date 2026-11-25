"use client";

import { useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { type Role, useReplaceUserRoles, useRoles, useUserRoles } from "@/features/access/api";
import { usePermissions } from "@/features/identity/permissions";

/**
 * The platform roles one user holds, as a checklist of every role that is not given in a place. Someone who may assign roles can change it; anyone else who may
 * see roles sees it read-only. Giving the administrator role needs being an administrator, which the API enforces and
 * explains if it refuses.
 */
export function UserRolesEditor({ userId }: { userId: string }) {
  const { has } = usePermissions();
  const roles = useRoles();
  const held = useUserRoles(userId);

  if (roles.isError) return <ErrorAlert error={roles.error} title="The roles could not be loaded" />;
  if (held.isError) return <ErrorAlert error={held.error} title="The user's roles could not be loaded" />;
  if (!roles.data || !held.data) return <Skeleton className="h-24 w-full" aria-label="Loading roles" />;

  // Keyed by what the server holds, so the checklist starts again from it after a save or another admin's change.
  const heldIds = held.data.map((role) => role.id);
  return (
    // Roles that apply within a place are given in the card below, with the place; here they would only be refused.
    <Checklist
      key={heldIds.join(",")}
      userId={userId}
      roles={roles.data.items.filter((role) => !role.scopeType)}
      initial={heldIds}
      editable={has("access.roles.assign")}
    />
  );
}

function Checklist({ userId, roles, initial, editable }: { userId: string; roles: Role[]; initial: string[]; editable: boolean }) {
  const [selected, setSelected] = useState<string[]>(initial);
  const replace = useReplaceUserRoles(userId);
  const changed = selected.length !== initial.length || selected.some((id) => !initial.includes(id));

  return (
    <form
      onSubmit={async (event) => {
        event.preventDefault();
        try {
          // Awaited, not given callbacks: saving refreshes the data, which starts this checklist again, and a callback
          // given to `mutate` is dropped for a component that has gone by then.
          await replace.mutateAsync(selected);
          toast.success("Roles saved");
        } catch {
          // The alert below shows what went wrong.
        }
      }}
      className="grid gap-3"
    >
      <fieldset className="grid gap-2" disabled={!editable || replace.isPending}>
        <legend className="sr-only">Roles</legend>
        {roles.map((role) => {
          const id = `user-role-${role.id}`;
          return (
            <div key={role.id} className="flex items-start gap-2">
              <Checkbox
                id={id}
                checked={selected.includes(role.id)}
                onCheckedChange={(checked) =>
                  setSelected(checked ? [...selected, role.id] : selected.filter((value) => value !== role.id))
                }
              />
              <Label htmlFor={id} className="grid gap-0.5 font-normal">
                <span className="font-medium">{role.name}</span>
                {role.description && <span className="text-sm text-muted-foreground">{role.description}</span>}
              </Label>
            </div>
          );
        })}
      </fieldset>
      {replace.isError && <ErrorAlert error={replace.error} title="The roles were not saved" />}
      {editable && (
        <div>
          <Button type="submit" disabled={!changed || replace.isPending}>
            {replace.isPending ? "Saving…" : "Save roles"}
          </Button>
        </div>
      )}
    </form>
  );
}
