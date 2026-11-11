import { describe, expect, it } from "vitest";

import { FEATURE_MODULES, isModulePath, moduleLinks } from "@/modules";

// These tests are about the registry's rules, so they walk whatever modules are listed: they hold with any of them
// removed, and with all of them removed (ADR 0024).
describe("the feature module registry", () => {
  it("shows a module's link only to a user who holds its permission", () => {
    for (const entry of FEATURE_MODULES) {
      if (!entry.nav) continue;
      const link = { href: entry.nav.href, label: entry.nav.label };
      expect(moduleLinks([entry.nav.permission])).toContainEqual(link);
      expect(moduleLinks([])).not.toContainEqual(link);
      expect(moduleLinks(["some.other.permission"])).not.toContainEqual(link);
    }
  });

  it("needs a signed-in user on the paths of a module and below them, and nowhere else", () => {
    for (const entry of FEATURE_MODULES) {
      for (const prefix of entry.protectedPrefixes) {
        expect(isModulePath(prefix)).toBe(true);
        expect(isModulePath(`${prefix}/anything/below`)).toBe(true);
        // A different path that merely starts with the same letters is not the module's.
        expect(isModulePath(`${prefix}-elsewhere`)).toBe(false);
      }
    }
    expect(isModulePath("/")).toBe(false);
    expect(isModulePath("/healthz")).toBe(false);
    expect(isModulePath("/auth/login")).toBe(false);
  });

  it("lists each module once, and gives each link a path that is also protected", () => {
    const ids = FEATURE_MODULES.map((entry) => entry.id);
    expect(new Set(ids).size).toBe(ids.length);
    for (const entry of FEATURE_MODULES) {
      if (entry.nav) expect(isModulePath(entry.nav.href)).toBe(true);
    }
  });
});
