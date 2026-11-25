import type { Issue } from "@/features/issues/api";
import { SESSION_LABEL } from "@/lib/labels";

/** Who an issue is about, such as "Theory · CSE-A" or "Lab · CSE-A1". */
export function audienceOf(issue: Pick<Issue, "sessionType" | "divisionCode" | "batchCode">): string {
  const code = issue.sessionType === "THEORY" ? issue.divisionCode : issue.batchCode;
  return code ? `${SESSION_LABEL[issue.sessionType]} · ${code}` : SESSION_LABEL[issue.sessionType];
}
