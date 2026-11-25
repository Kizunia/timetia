"use client";

import { useSearchParams } from "next/navigation";

import { VersionDetail } from "@/features/setup/components/VersionDetail";
import { VersionList } from "@/features/setup/components/VersionList";

/** The weekly timetables of the divisions: the list of versions, or one version when `?version=` names it. */
export function TimetablesScreen() {
  const versionId = useSearchParams().get("version");
  return versionId ? <VersionDetail id={versionId} /> : <VersionList />;
}
