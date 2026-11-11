import { TriangleAlertIcon } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { ApiError, messageFor } from "@/lib/api/problem";

/**
 * Says why a request failed, in words chosen from the problem's `code` (ADR 0043), and offers the trace ID so a report can
 * be found in the logs. A failure that is not an API answer, such as a lost connection, gets the generic text.
 */
export function ErrorAlert({ error, title = "That did not work" }: { error: unknown; title?: string }) {
  const traceId = error instanceof ApiError ? error.traceId : undefined;
  return (
    <Alert variant="destructive" role="alert">
      <TriangleAlertIcon aria-hidden="true" />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>
        <p>{messageFor(error)}</p>
        {traceId && <p className="mt-1 text-xs">Reference: {traceId}</p>}
      </AlertDescription>
    </Alert>
  );
}
