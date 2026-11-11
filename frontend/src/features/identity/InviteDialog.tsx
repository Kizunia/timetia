"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useRoles } from "@/features/access/api";
import { useInviteUser } from "@/features/identity/api";
import { usePermissions } from "@/features/identity/permissions";
import { applyProblem } from "@/lib/forms";

/** The form's own checks are for usability; the server's validation is authoritative and is shown too. */
const schema = z.object({
  email: z.string().trim().min(1, "Enter an email address").max(254, "That address is too long").pipe(z.email("Enter a valid email address")),
  roleIds: z.array(z.string()),
});

type Values = z.input<typeof schema>;

/**
 * Invites someone by email: Keycloak creates the account and mails them a link to set a password. Roles to give them
 * besides the built-in user role are offered to someone who may assign roles.
 */
export function InviteDialog() {
  const { has } = usePermissions();
  const canChooseRoles = has("access.roles.view") && has("access.roles.assign");
  const roles = useRoles(canChooseRoles);
  const invite = useInviteUser();
  const [open, setOpen] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const form = useForm<Values, unknown, z.output<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", roleIds: [] },
  });

  function close(next: boolean) {
    setOpen(next);
    if (!next) {
      form.reset();
      setFailure(null);
      invite.reset();
    }
  }

  const submit = form.handleSubmit((values) => {
    setFailure(null);
    invite.mutate(
      { email: values.email, roleIds: values.roleIds },
      {
        onSuccess: (user) => {
          toast.success(`Invitation sent to ${user.email ?? values.email}`);
          close(false);
        },
        onError: (error) =>
          setFailure(
            applyProblem(error, ["email"], (field, message) => form.setError(field as "email", { message }), {
              "identity.email_in_use": "email",
            }),
          ),
      },
    );
  });

  const chosen = useWatch({ control: form.control, name: "roleIds" });
  const selectable = (roles.data?.items ?? []).filter((role) => role.builtIn !== "USER");

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogTrigger asChild>
        <Button type="button">Invite user</Button>
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={submit} noValidate className="grid gap-4">
          <DialogHeader>
            <DialogTitle>Invite a user</DialogTitle>
            <DialogDescription>
              They get an email with a link to set a password. They start with the built-in user role.
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-2">
            <Label htmlFor="invite-email">Email address</Label>
            <Input
              id="invite-email"
              type="email"
              autoComplete="off"
              aria-invalid={form.formState.errors.email ? true : undefined}
              aria-describedby={form.formState.errors.email ? "invite-email-error" : undefined}
              {...form.register("email")}
            />
            {form.formState.errors.email && (
              <p id="invite-email-error" role="alert" className="text-sm text-destructive">
                {form.formState.errors.email.message}
              </p>
            )}
          </div>

          {canChooseRoles && selectable.length > 0 && (
            <fieldset className="grid gap-2">
              <legend className="mb-1 text-sm font-medium">Roles to give them as well</legend>
              {selectable.map((role) => {
                const id = `invite-role-${role.id}`;
                return (
                  <div key={role.id} className="flex items-center gap-2">
                    <Checkbox
                      id={id}
                      checked={chosen.includes(role.id)}
                      onCheckedChange={(checked) =>
                        form.setValue("roleIds", checked ? [...chosen, role.id] : chosen.filter((value) => value !== role.id))
                      }
                    />
                    <Label htmlFor={id} className="font-normal">
                      {role.name}
                    </Label>
                  </div>
                );
              })}
            </fieldset>
          )}

          {failure && <ErrorAlert error={invite.error} title="The invitation was not sent" />}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => close(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={invite.isPending}>
              {invite.isPending ? "Sending…" : "Send invitation"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
