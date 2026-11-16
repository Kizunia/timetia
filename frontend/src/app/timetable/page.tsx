import { Suspense } from "react";

import { TimetableWithTickets } from "@/app/timetable/TimetableWithTickets";
import { PageShell } from "@/components/PageShell";

export const dynamic = "force-dynamic";

export default function TimetablePage() {
  return (
    <PageShell width="wide">
      {/* The screen reads the `lecture` query parameter, which needs a Suspense boundary during the build. */}
      <Suspense>
        <TimetableWithTickets />
      </Suspense>
    </PageShell>
  );
}
