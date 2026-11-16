import type { ScopedRole } from "@/features/campus/api";

export interface Places {
  divisionIds: string[];
  batchIds: string[];
  departmentIds: string[];
  roles: string[];
}

/** The places and role names in a person's scoped roles. A role that applies everywhere has no place. */
export function placesOf(roles: readonly ScopedRole[] | undefined): Places {
  const idsOf = (type: string) => [
    ...new Set((roles ?? []).filter((role) => role.scopeType === type && role.scopeId).map((role) => role.scopeId!)),
  ];
  return {
    divisionIds: idsOf("DIVISION"),
    batchIds: idsOf("BATCH"),
    departmentIds: idsOf("DEPARTMENT"),
    roles: [...new Set((roles ?? []).map((role) => role.roleName))],
  };
}
