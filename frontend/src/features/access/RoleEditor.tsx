"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { ErrorAlert } from "@/components/ErrorAlert";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import {
  type PermissionInfo,
  type Role,
  useCreateRole,
  useDeleteRole,
  usePermissionCatalogue,
  useRole,
  useUpdateRole,
} from "@/features/access/api";
import { usePermissions } from "@/features/identity/permissions";
import { applyProblem } from "@/lib/forms";

/** The form's own checks are for usability; the server's validation is authoritative and is shown too. */
const schema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Enter a name")
    .max(80, "Use at most 80 characters")
    .regex(/^[A-Za-z0-9][A-Za-z0-9 ._-]*$/, "Use letters, digits, spaces, dots, dashes and underscores"),
  description: z.string().max(255, "Use at most 255 characters"),
  permissions: z.array(z.string()),
});

type Values = z.input<typeof schema>;

/** A role to create (no id) or to look at and change. */
export function RoleEditor({ roleId }: { roleId?: string }) {
  const catalogue = usePermissionCatalogue();
  const role = useRole(roleId);

  return (
    <section aria-labelledby="role-heading" className="grid gap-6">
      <p>
        <Link href="/admin/roles" className="text-sm font-medium text-primary underline underline-offset-2">
          ← All roles
        </Link>
      </p>
      {catalogue.isError && <ErrorAlert error={catalogue.error} title="The permissions could not be loaded" />}
      {roleId && role.isError && <ErrorAlert error={role.error} title="The role could not be loaded" />}
      {(catalogue.isPending || (roleId && role.isPending)) && <Skeleton className="h-48 w-full" aria-label="Loading the role" />}
      {catalogue.data && (!roleId || role.data) && (
        // Keyed by the version, so the form starts again from the server's role after a save.
        <RoleForm key={role.data ? `${role.data.id}-${role.data.version}` : "new"} role={role.data} catalogue={catalogue.data} />
      )}
    </section>
  );
}

function RoleForm({ role, catalogue }: { role?: Role; catalogue: PermissionInfo[] }) {
  const router = useRouter();
  const { has } = usePermissions();
  const create = useCreateRole();
  const update = useUpdateRole(role?.id ?? "");
  const remove = useDeleteRole(role?.id ?? "");
  const [failure, setFailure] = useState<string | null>(null);

  const isAdminRole = role?.builtIn === "ADMIN";
  const readOnly = !has("access.roles.manage") || isAdminRole;
  const form = useForm<Values, unknown, z.output<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { name: role?.name ?? "", description: role?.description ?? "", permissions: role?.permissions ?? [] },
  });

  const groups = new Map<string, PermissionInfo[]>();
  for (const permission of catalogue) {
    const area = permission.name.split(".")[0] ?? "other";
    groups.set(area, [...(groups.get(area) ?? []), permission]);
  }

  const mutation = role ? update : create;
  const submit = form.handleSubmit(async (values) => {
    setFailure(null);
    const input = { name: values.name, description: values.description, permissions: values.permissions };
    try {
      // Awaited, not given callbacks: saving refreshes the role, which starts this form again with the new version, and a
      // callback given to `mutate` is dropped for a form that has gone by then.
      const saved = role ? await update.mutateAsync({ ...input, version: role.version }) : await create.mutateAsync(input);
      toast.success(role ? "Role saved" : "Role created");
      if (!role) router.push(`/admin/roles/${saved.id}`);
    } catch (error) {
      setFailure(
        applyProblem(error, ["name", "description", "permissions"], (field, message) => form.setError(field as "name", { message }), {
          "access.role_name_in_use": "name",
        }),
      );
    }
  });

  return (
    <form onSubmit={submit} noValidate className="grid gap-6">
      <div className="flex flex-wrap items-center gap-3">
        <h1 id="role-heading" className="text-2xl font-semibold tracking-tight">
          {role ? role.name : "New role"}
        </h1>
        {role?.builtIn && <Badge variant="secondary">built in</Badge>}
        {role && <span className="text-sm text-muted-foreground">{role.assignedUsers} users hold it</span>}
      </div>

      {isAdminRole && (
        <p className="max-w-prose text-sm text-muted-foreground">
          The administrator role holds every permission and cannot be changed or deleted. Someone is always an administrator.
        </p>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Details</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4">
          <div className="grid gap-2">
            <Label htmlFor="role-name">Name</Label>
            <Input
              id="role-name"
              disabled={readOnly || role?.builtIn === "USER"}
              aria-invalid={form.formState.errors.name ? true : undefined}
              aria-describedby={form.formState.errors.name ? "role-name-error" : undefined}
              {...form.register("name")}
            />
            {form.formState.errors.name && (
              <p id="role-name-error" role="alert" className="text-sm text-destructive">
                {form.formState.errors.name.message}
              </p>
            )}
          </div>
          <div className="grid gap-2">
            <Label htmlFor="role-description">Description</Label>
            <Textarea id="role-description" rows={2} disabled={readOnly} {...form.register("description")} />
            {form.formState.errors.description && (
              <p role="alert" className="text-sm text-destructive">
                {form.formState.errors.description.message}
              </p>
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Permissions</CardTitle>
          <CardDescription>
            You can only give a role permissions you hold yourself.{" "}
            {!has("access.roles.manage") && "You can look at this role but not change it."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Controller
            control={form.control}
            name="permissions"
            render={({ field }) => (
              <div className="grid gap-5">
                {[...groups].map(([area, permissions]) => (
                  <fieldset key={area} className="grid gap-2" disabled={readOnly}>
                    <legend className="mb-1 text-sm font-semibold capitalize">{area}</legend>
                    {permissions.map((permission) => {
                      const id = `perm-${permission.name}`;
                      const selected = field.value.includes(permission.name);
                      return (
                        <div key={permission.name} className="flex items-start gap-2">
                          <Checkbox
                            id={id}
                            checked={selected}
                            // A permission the editor does not hold cannot be added; one already there can be removed.
                            disabled={!selected && !has(permission.name)}
                            onCheckedChange={(checked) =>
                              field.onChange(checked ? [...field.value, permission.name] : field.value.filter((name) => name !== permission.name))
                            }
                          />
                          <Label htmlFor={id} className="grid gap-0.5 font-normal">
                            <span>{permission.description}</span>
                            <span className="font-mono text-xs text-muted-foreground">{permission.name}</span>
                          </Label>
                        </div>
                      );
                    })}
                  </fieldset>
                ))}
              </div>
            )}
          />
          {form.formState.errors.permissions && (
            <p role="alert" className="mt-2 text-sm text-destructive">
              {form.formState.errors.permissions.message}
            </p>
          )}
        </CardContent>
      </Card>

      {failure && <ErrorAlert error={mutation.error} title="The role was not saved" />}
      {remove.isError && <ErrorAlert error={remove.error} title="The role was not deleted" />}

      {!readOnly && (
        <div className="flex flex-wrap items-center gap-3">
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? "Saving…" : role ? "Save role" : "Create role"}
          </Button>
          {role && !role.builtIn && has("access.roles.manage") && (
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button type="button" variant="destructive" disabled={remove.isPending}>
                  Delete role
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Delete this role?</AlertDialogTitle>
                  <AlertDialogDescription>
                    {role.assignedUsers > 0
                      ? `${role.assignedUsers} users still hold it, so it cannot be deleted until you take it away from them.`
                      : "Nobody holds it. It can no longer be given to anyone."}
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Cancel</AlertDialogCancel>
                  <AlertDialogAction
                    onClick={async () => {
                      try {
                        await remove.mutateAsync();
                        toast.success("Role deleted");
                        router.push("/admin/roles");
                      } catch {
                        // The alert above shows what went wrong.
                      }
                    }}
                  >
                    Delete
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          )}
        </div>
      )}
    </form>
  );
}
