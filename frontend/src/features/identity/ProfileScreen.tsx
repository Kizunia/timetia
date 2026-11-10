"use client";

import { useRouter } from "next/navigation";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useUpdateTheme } from "@/features/identity/api";
import { usePermissions } from "@/features/identity/permissions";
import type { Me } from "@/lib/api/client";

const THEMES: { value: Me["theme"]; label: string }[] = [
  { value: "SYSTEM", label: "Same as my device" },
  { value: "LIGHT", label: "Light" },
  { value: "DARK", label: "Dark" },
];

/**
 * The signed-in user's own profile (ADR 0041). Name, email and password belong to Keycloak and are changed in its account
 * console, which is linked; what Kibootia keeps is the preferences. Viewing it works before the email address is verified,
 * changing a preference does not.
 */
export function ProfileScreen({ accountUrl }: { accountUrl: string }) {
  const { me } = usePermissions();
  const router = useRouter();
  const theme = useUpdateTheme();

  return (
    <section aria-labelledby="profile-heading" className="grid gap-6">
      <h1 id="profile-heading" className="text-3xl font-semibold tracking-tight">
        Your profile
      </h1>

      <Card>
        <CardHeader>
          <CardTitle>Account</CardTitle>
          <CardDescription>
            Your name, email address and password are kept by Keycloak.{" "}
            <a href={accountUrl} className="font-medium text-primary underline underline-offset-2">
              Change them in your account console
            </a>
            .
          </CardDescription>
        </CardHeader>
        <CardContent>
          <dl data-testid="profile" className="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1">
            <dt className="text-muted-foreground">Name</dt>
            <dd data-field="name">{me.name}</dd>
            <dt className="text-muted-foreground">Email</dt>
            <dd data-field="email">
              {me.email}{" "}
              <Badge variant={me.emailVerified ? "outline" : "secondary"}>{me.emailVerified ? "verified" : "not verified"}</Badge>
            </dd>
            <dt className="text-muted-foreground">Username</dt>
            <dd data-field="username">{me.username}</dd>
            <dt className="text-muted-foreground">Roles</dt>
            <dd data-field="roles" className="flex flex-wrap gap-1">
              {me.roles.map((role) => (
                <Badge key={role.id} variant="outline">
                  {role.name}
                </Badge>
              ))}
            </dd>
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Preferences</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3">
          <div className="grid gap-1.5">
            <Label htmlFor="theme">Colour scheme</Label>
            <Select
              value={me.theme}
              disabled={theme.isPending || !me.emailVerified}
              onValueChange={(value) =>
                theme.mutate(value as Me["theme"], {
                  onSuccess: () => {
                    toast.success("Saved");
                    router.refresh();
                  },
                })
              }
            >
              <SelectTrigger id="theme" className="w-60">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {THEMES.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {!me.emailVerified && <p className="text-sm text-muted-foreground">Verify your email address to change preferences.</p>}
          {theme.isError && <ErrorAlert error={theme.error} title="The preference was not saved" />}
        </CardContent>
      </Card>
    </section>
  );
}
