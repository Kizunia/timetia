import { ErrorAlert } from "@/components/ErrorAlert";
import { ApiError } from "@/lib/api/problem";

/** For a clash the server's own words say who or what would be booked twice, so they are shown as they are. */
export function TimetableError({ error, title }: { error: unknown; title: string }) {
  const clash = error instanceof ApiError && error.code === "timetable.clash" ? error.problem?.detail : undefined;
  return <ErrorAlert error={error} title={title} message={clash} />;
}
