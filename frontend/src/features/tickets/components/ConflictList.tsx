import { TriangleAlertIcon } from "lucide-react";
import Link from "next/link";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import type { components } from "@/lib/api/schema.gen";
import { CONFLICT_LABEL } from "@/lib/labels";

export interface ConflictItem {
  type: components["schemas"]["ConflictResponse"]["type"];
  message: string;
  conflictingLectureId?: string | null;
  conflictingTicketId?: string | null;
}

/**
 * Everything that is in the way of a change. The server's sentence is shown as it is; the lecture or ticket it clashes with is
 * linked when the server says which. Nobody can override a conflict: the way out is to edit the ticket.
 */
export function ConflictList({ conflicts }: { conflicts: readonly ConflictItem[] }) {
  if (conflicts.length === 0) return null;
  return (
    <Alert variant="destructive">
      <TriangleAlertIcon aria-hidden="true" />
      <AlertTitle>Something is in the way</AlertTitle>
      <AlertDescription>
        <ul className="mt-1 flex flex-col gap-2">
          {conflicts.map((conflict, index) => (
            <li key={index}>
              <span className="font-semibold">{CONFLICT_LABEL[conflict.type] ?? conflict.type}</span>
              {": "}
              {conflict.message}
              {conflict.conflictingLectureId && (
                <>
                  {" "}
                  <Link className="underline underline-offset-2" href={`/timetable?lecture=${conflict.conflictingLectureId}`}>
                    See that lecture
                  </Link>
                </>
              )}
              {conflict.conflictingTicketId && (
                <>
                  {" "}
                  <Link className="underline underline-offset-2" href={`/tickets/${conflict.conflictingTicketId}`}>
                    See that ticket
                  </Link>
                </>
              )}
            </li>
          ))}
        </ul>
      </AlertDescription>
    </Alert>
  );
}
