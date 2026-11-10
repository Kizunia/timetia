import Link from "next/link";

import { SignOutButton } from "@/components/SignOutButton";
import { UserAvatar } from "@/features/identity/UserAvatar";
import type { Me } from "@/lib/api/client";

/** The sections of the administration area and the permission that shows each one. */
export const ADMIN_SECTIONS = [
  { href: "/admin/users", label: "Users", permission: "identity.profiles.view" },
  { href: "/admin/roles", label: "Roles", permission: "access.roles.view" },
  { href: "/admin/audit", label: "Audit log", permission: "audit.entries.view" },
] as const;

const linkClass =
  "rounded-md px-2 py-1 text-sm font-medium text-foreground/80 hover:bg-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring";

/**
 * The top of every page. What it shows depends on the signed-in user's permissions, which is for usability only: the
 * backend refuses what a hidden link would have led to.
 */
export function SiteHeader({ me }: { me: Me | null }) {
  const sections = me ? ADMIN_SECTIONS.filter((section) => me.permissions.includes(section.permission)) : [];
  return (
    <header className="border-b">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3 sm:px-6">
        <Link href="/" className="text-lg font-semibold tracking-tight">
          Kibootia
        </Link>
        <nav aria-label="Main" className="flex flex-1 flex-wrap items-center gap-1">
          {me && (
            <Link href="/profile" className={linkClass}>
              Profile
            </Link>
          )}
          {sections.length > 0 && (
            <Link href={sections[0]!.href} className={linkClass}>
              Administration
            </Link>
          )}
        </nav>
        {me && (
          <div className="flex items-center gap-3">
            <UserAvatar userId={me.id} name={me.name} version={me.avatarsEnabled ? me.avatarVersion : null} />
            <span className="text-sm text-muted-foreground" data-testid="header-name">
              {me.name}
            </span>
            <SignOutButton />
          </div>
        )}
      </div>
    </header>
  );
}
