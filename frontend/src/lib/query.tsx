"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";

import { ApiError } from "@/lib/api/problem";

/**
 * One query client per browser tab (ADR 0038). Reads are kept fresh for a short time, and a request the API refused for
 * a reason retrying cannot fix (401, 403, 404, 409) is not repeated.
 */
export function QueryProvider({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 15_000,
            refetchOnWindowFocus: false,
            retry: (failures, error) => {
              if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false;
              return failures < 2;
            },
          },
        },
      }),
  );
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
