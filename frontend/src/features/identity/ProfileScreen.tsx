"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useRemoveAvatar, useUpdateTheme, useUploadAvatar } from "@/features/identity/api";
import { AVATAR_TYPES, checkAvatarFile } from "@/features/identity/avatar";
import { usePermissions } from "@/features/identity/permissions";
import { UserAvatar } from "@/features/identity/UserAvatar";
import type { Me } from "@/lib/api/client";

const THEMES: { value: Me["theme"]; label: string }[] = [
  { value: "SYSTEM", label: "Same as my device" },
  { value: "LIGHT", label: "Light" },
  { value: "DARK", label: "Dark" },
];

/**
 * The signed-in user's own profile (ADR 0041). Name, email and password belong to Keycloak and are changed in its account
 * console, which is linked; what Kibootia keeps is the photo and the preferences. Viewing it works before the email
 * address is verified, changing the photo or a preference does not.
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

      {me.avatarsEnabled && <PhotoCard />}

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

/**
 * The user's photo (ADR 0029). The browser only checks type and size so that an obvious mistake is answered at once; the
 * API decides from the file's bytes and always checks again. The photo is resized and stripped of its metadata on the
 * server, which the text says so nobody wonders where their location went.
 *
 * Saving awaits the call instead of passing callbacks, because the refresh that follows replaces this screen's data and
 * TanStack Query drops the callbacks of a mutation whose component has been remounted (ADR 0050).
 */
function PhotoCard() {
  const { me } = usePermissions();
  const router = useRouter();
  const upload = useUploadAvatar();
  const remove = useRemoveAvatar();
  const input = useRef<HTMLInputElement>(null);
  const [refusal, setRefusal] = useState<string | null>(null);
  const busy = upload.isPending || remove.isPending;
  const hasPhoto = Boolean(me.avatarVersion);

  async function choose(file: File | undefined) {
    if (!file) return;
    const problem = checkAvatarFile(file);
    setRefusal(problem);
    remove.reset();
    if (problem) return;
    try {
      await upload.mutateAsync(file);
      toast.success("Photo saved");
      router.refresh();
    } catch {
      // The reason is shown below, from the mutation's error.
    }
  }

  async function removePhoto() {
    setRefusal(null);
    upload.reset();
    try {
      await remove.mutateAsync();
      toast.success("Photo removed");
      router.refresh();
    } catch {
      // The reason is shown below, from the mutation's error.
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Photo</CardTitle>
        <CardDescription>
          JPEG, PNG or WebP, up to 5 MB. It is cut to a square and resized, and what the camera recorded in it, such as the
          location, is removed.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        <div className="flex flex-wrap items-center gap-4">
          <UserAvatar userId={me.id} name={me.name} version={me.avatarVersion} size="large" label="Your photo" />
          <div className="flex flex-wrap gap-2">
            <Button type="button" disabled={busy || !me.emailVerified} onClick={() => input.current?.click()}>
              {upload.isPending ? "Uploading…" : hasPhoto ? "Replace photo" : "Upload a photo"}
            </Button>
            {hasPhoto && (
              <Button type="button" variant="outline" disabled={busy || !me.emailVerified} onClick={() => void removePhoto()}>
                Remove photo
              </Button>
            )}
            <input
              ref={input}
              type="file"
              accept={AVATAR_TYPES.join(",")}
              className="hidden"
              data-testid="avatar-input"
              aria-label="Photo file"
              tabIndex={-1}
              onChange={(event) => {
                const file = event.target.files?.[0];
                // Cleared so that choosing the same file again, after a failure, still counts as a choice.
                event.target.value = "";
                void choose(file);
              }}
            />
          </div>
        </div>
        {!me.emailVerified && <p className="text-sm text-muted-foreground">Verify your email address to change your photo.</p>}
        {refusal && (
          <p role="alert" className="text-sm text-destructive" data-testid="avatar-refusal">
            {refusal}
          </p>
        )}
        {upload.isError && <ErrorAlert error={upload.error} title="The photo was not saved" />}
        {remove.isError && <ErrorAlert error={remove.error} title="The photo was not removed" />}
      </CardContent>
    </Card>
  );
}
