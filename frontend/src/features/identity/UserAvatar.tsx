"use client";

import { useState } from "react";

import { cn } from "@/lib/utils";

const SIZES = {
  small: "size-8 text-xs",
  large: "size-24 text-2xl",
} as const;

export type AvatarSize = keyof typeof SIZES;

/** The one or two letters shown when there is no photo: the first letters of the first two words of the name. */
export function initials(name: string): string {
  const letters = name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => Array.from(word)[0] ?? "");
  return letters.join("").toUpperCase() || "?";
}

/** Where a user's avatar is served: the same-origin proxy, which adds the session's token and checks permission each time. */
export function avatarUrl(userId: string, size: AvatarSize, version: string): string {
  return `/api/v1/users/${encodeURIComponent(userId)}/avatar?size=${size.toUpperCase()}&v=${encodeURIComponent(version)}`;
}

/**
 * A user's photo, or their initials when they have none or it cannot be loaded (ADR 0029, ADR 0041).
 *
 * The image is a plain `<img>` on purpose, not `next/image`: it is private to the signed-in viewer, so it must be fetched
 * by the browser with the session cookie and never go through the image optimizer, which fetches without the cookie and
 * would keep a copy for everyone. `version` changes whenever the avatar does, which is what refreshes a cached copy.
 *
 * @param version the profile's `avatarVersion`; null when there is no avatar, or when avatars are switched off
 * @param label what to announce for the picture; leave it out when the name is shown next to it
 */
export function UserAvatar({
  userId,
  name,
  version,
  size = "small",
  label,
  className,
}: {
  userId: string;
  name: string;
  version: string | null | undefined;
  size?: AvatarSize;
  label?: string;
  className?: string;
}) {
  // Remember which version failed, so a new upload gets a fresh try.
  const [failedVersion, setFailedVersion] = useState<string | null>(null);
  const showImage = Boolean(version) && failedVersion !== version;
  return (
    <span
      data-testid="avatar"
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      className={cn(
        "relative inline-flex shrink-0 select-none items-center justify-center overflow-hidden rounded-full bg-muted font-medium text-foreground",
        SIZES[size],
        className,
      )}
    >
      {showImage && version ? (
        // eslint-disable-next-line @next/next/no-img-element -- private image; see the note above
        <img
          src={avatarUrl(userId, size, version)}
          alt=""
          className="size-full object-cover"
          onError={() => setFailedVersion(version)}
        />
      ) : (
        <span aria-hidden="true">{initials(name)}</span>
      )}
    </span>
  );
}
