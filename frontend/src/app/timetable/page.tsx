import { Suspense } from "react";

import { PageShell } from "@/components/PageShell";
import { TimetableScreen } from "@/features/timetable/TimetableScreen";

export const dynamic = "force-dynamic";

export default function TimetablePage() {
  return (
    <PageShell width="wide">
      {/* The screen reads the `lecture` query parameter, which needs a Suspense boundary during the build. */}
      <Suspense>
        <TimetableScreen />
      </Suspense>
    </PageShell>
  );
}
