import type { Issue } from "@/features/issues/api";

export interface IssueActions {
  close: boolean;
  mention: boolean;
  link: boolean;
}

/**
 * Which buttons an issue gets for this person: the one who opened it may close it, mention more teachers and link a ticket;
 * a mentioned teacher may close it and link a ticket. A closed issue has none. For usability only; the server decides.
 */
export function issueActions(
  issue: Pick<Issue, "status" | "openerUserId" | "mentionedFacultyIds">,
  userId: string,
  myFacultyIds: readonly string[],
): IssueActions {
  const open = issue.status === "OPEN";
  const opener = issue.openerUserId === userId;
  const mentioned = issue.mentionedFacultyIds.some((id) => myFacultyIds.includes(id));
  const answers = open && (opener || mentioned);
  return { close: answers, link: answers, mention: open && opener };
}
