"use client";

import { Button } from "@/components/ui/button";
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
import type { User, UserAction } from "@/features/identity/api";

/** One step an administrator takes on a user, behind a confirmation that says what it will do. */
interface ActionSpec {
  action: UserAction;
  label: string;
  title: string;
  description: string;
  confirm: string;
  permission: string;
  destructive?: boolean;
  /** Which user states the action applies to. */
  states: readonly User["state"][];
  /** Not offered for the signed-in user's own account. */
  notSelf?: boolean;
}

const ACTIONS: readonly ActionSpec[] = [
  {
    action: "resend-invitation",
    label: "Send the invitation again",
    title: "Send the invitation again?",
    description: "Keycloak emails them a new link to set their password.",
    confirm: "Send it",
    permission: "identity.users.invite",
    states: ["INVITED"],
  },
  {
    action: "disable",
    label: "Disable",
    title: "Disable this user?",
    description: "They are signed out and refused from their next request. You can enable them again.",
    confirm: "Disable",
    permission: "identity.users.disable",
    states: ["INVITED", "ACTIVE"],
    notSelf: true,
  },
  {
    action: "enable",
    label: "Enable",
    title: "Enable this user?",
    description: "They can sign in again.",
    confirm: "Enable",
    permission: "identity.users.disable",
    states: ["DISABLED"],
  },
  {
    action: "delete",
    label: "Delete",
    title: "Delete this user?",
    description: "They are disabled and hidden. You can restore them until they are erased.",
    confirm: "Delete",
    permission: "identity.users.delete",
    destructive: true,
    states: ["INVITED", "ACTIVE", "DISABLED"],
    notSelf: true,
  },
  {
    action: "restore",
    label: "Restore",
    title: "Restore this user?",
    description: "Their account and roles come back as they were.",
    confirm: "Restore",
    permission: "identity.users.restore",
    states: ["DELETED"],
  },
  {
    action: "erase",
    label: "Erase for good",
    title: "Erase this user for good?",
    description:
      "Their account, profile, roles and everything they own are removed and cannot be brought back. The audit log keeps only their ID, shown as a deleted user.",
    confirm: "Erase for good",
    permission: "identity.users.erase",
    destructive: true,
    states: ["DELETED"],
  },
];

/**
 * The actions an administrator may take on this user: those that apply to the user's state and that the administrator holds
 * the permission for (usability only; the API refuses the rest). An erasure that has begun offers nothing, and no one is
 * offered disable or delete on their own account.
 */
export function availableActions(user: User, selfId: string, can: (permission: string) => boolean): readonly ActionSpec[] {
  if (user.erasureBegun) return [];
  return ACTIONS.filter(
    (spec) => spec.states.includes(user.state) && can(spec.permission) && !(spec.notSelf && user.id === selfId),
  );
}

export function UserActions({
  user,
  selfId,
  can,
  busy,
  onAction,
}: {
  user: User;
  selfId: string;
  can: (permission: string) => boolean;
  busy: boolean;
  onAction: (action: UserAction) => void;
}) {
  const actions = availableActions(user, selfId, can);
  if (actions.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        {user.erasureBegun ? "Erasing this user is under way." : "There is nothing you can do with this user."}
      </p>
    );
  }
  return (
    <div className="flex flex-wrap gap-2">
      {actions.map((spec) => (
        <AlertDialog key={spec.action}>
          <AlertDialogTrigger asChild>
            <Button type="button" variant={spec.destructive ? "destructive" : "outline"} disabled={busy}>
              {spec.label}
            </Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>{spec.title}</AlertDialogTitle>
              <AlertDialogDescription>{spec.description}</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction onClick={() => onAction(spec.action)}>{spec.confirm}</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      ))}
    </div>
  );
}
