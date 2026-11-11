"use client";

import { Button } from "@/components/ui/button";

/** Shown when a page could not load, for example because the API could not be reached. */
export default function ErrorPage({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main id="main" className="mx-auto max-w-lg px-4 py-24 sm:px-6">
      <h1 className="text-2xl font-semibold tracking-tight">Something went wrong</h1>
      <p className="mt-3 text-muted-foreground">The page could not be loaded. Try again in a moment.</p>
      <div className="mt-6">
        <Button type="button" onClick={() => reset()}>
          Try again
        </Button>
      </div>
    </main>
  );
}
