"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { visibleAdminSections } from "@/components/SiteHeader";
import { usePermissions } from "@/features/identity/permissions";
import { cn } from "@/lib/utils";

/**
 * The frame of the administration area: navigation to the sections the signed-in user may see. A user who may see none is
 * told so instead of getting a page of errors. Hiding links is for usability only; the API refuses the rest.
 */
export function AdminFrame({ children }: { children: React.ReactNode }) {
  const { me } = usePermissions();
  const pathname = usePathname();
  const sections = visibleAdminSections(me);

  if (sections.length === 0) {
    return (
      <section aria-labelledby="no-access-heading">
        <h1 id="no-access-heading" className="text-2xl font-semibold tracking-tight">
          You do not have access to administration
        </h1>
        <p className="mt-2 text-muted-foreground">Ask an administrator for a role that includes it.</p>
      </section>
    );
  }

  return (
    <div className="grid gap-8 md:grid-cols-[12rem_1fr]">
      <nav aria-label="Administration" className="flex gap-1 md:flex-col">
        {sections.map((section) => {
          const current = pathname === section.href || pathname.startsWith(`${section.href}/`);
          return (
            <Link
              key={section.href}
              href={section.href}
              aria-current={current ? "page" : undefined}
              className={cn(
                "rounded-md px-3 py-2 text-sm font-medium hover:bg-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring",
                current ? "bg-muted text-foreground" : "text-foreground/80",
              )}
            >
              {section.label}
            </Link>
          );
        })}
      </nav>
      <div>{children}</div>
    </div>
  );
}
