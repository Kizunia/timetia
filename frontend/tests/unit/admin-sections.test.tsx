import { describe, expect, it } from "vitest";

import { visibleAdminSections } from "@/components/SiteHeader";

const all = ["identity.profiles.view", "access.roles.view", "identity.users.invite", "audit.entries.view"];

describe("the sections of the administration area a user is shown", () => {
  it("shows the import only to an administrator, even to someone who holds the permission to invite", () => {
    const labels = (administrator: boolean) => visibleAdminSections({ permissions: all, administrator }).map((section) => section.label);

    expect(labels(true)).toEqual(["Users", "Roles", "Import", "Audit log"]);
    expect(labels(false)).toEqual(["Users", "Roles", "Audit log"]);
  });

  it("shows each of the others for its own permission", () => {
    expect(visibleAdminSections({ permissions: ["access.roles.view"], administrator: false }).map((section) => section.label)).toEqual([
      "Roles",
    ]);
    expect(visibleAdminSections({ permissions: [], administrator: true })).toEqual([]);
    expect(visibleAdminSections({ permissions: ["identity.users.invite"], administrator: false })).toEqual([]);
  });
});
