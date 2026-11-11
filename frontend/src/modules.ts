/**
 * The feature modules that have screens of their own, and the one place the shared shell learns about them: the main
 * navigation reads `nav`, the sign-in middleware reads `protectedPrefixes`. A module that is removed (ADR 0024, and
 * `kibootia-docs/recipes`) leaves this list by deleting its block, and the shell needs no other change.
 *
 * The block of each removable module sits between `module:<name>:start` and `module:<name>:end`, which is what
 * `scripts/remove-module.mjs` looks for. Keep each module's entry inside its own tagged block.
 *
 * The identity, access and audit screens are core and are part of the shell (`SiteHeader`, the administration area),
 * so they are not listed here.
 */
export interface FeatureModule {
  /** The module's name, the same as its folder in `src/features`. */
  id: string;
  /** The link in the main navigation, shown to a user who holds the permission. */
  nav?: { href: string; label: string; permission: string };
  /** Paths that need a signed-in user: the path itself and everything below it. */
  protectedPrefixes: readonly string[];
}

export const FEATURE_MODULES: readonly FeatureModule[] = [
  // module:notes:start
  {
    id: "notes",
    nav: { href: "/notes", label: "Notes", permission: "notes.notes.view" },
    protectedPrefixes: ["/notes"],
  },
  // module:notes:end
];

/** The navigation links a user may see, in the order the modules are listed. */
export function moduleLinks(permissions: readonly string[]): { href: string; label: string }[] {
  return FEATURE_MODULES.flatMap((module) =>
    module.nav && permissions.includes(module.nav.permission) ? [{ href: module.nav.href, label: module.nav.label }] : [],
  );
}

/** Whether a path belongs to a feature module and so needs a signed-in user. */
export function isModulePath(pathname: string): boolean {
  return FEATURE_MODULES.some((module) =>
    module.protectedPrefixes.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`)),
  );
}
