import { Suspense } from "react";

import { TimetablesScreen } from "@/features/setup/TimetablesScreen";

export default function TimetablesPage() {
  // The screen reads `?version=` from the address, which Next.js asks to be under a Suspense boundary.
  return (
    <Suspense>
      <TimetablesScreen />
    </Suspense>
  );
}
