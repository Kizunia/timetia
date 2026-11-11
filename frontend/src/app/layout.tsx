import type { Metadata } from "next";
import { ThemeProvider } from "next-themes";

import "./globals.css";
import { Toaster } from "@/components/ui/sonner";
import { QueryProvider } from "@/lib/query";

export const metadata: Metadata = {
  title: "Timetia",
  description: "Timetia lecture rescheduling",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    // The theme class is set on <html> before hydration, so the first paint already has the right colours.
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-screen bg-background font-sans text-foreground antialiased">
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:bg-background focus:px-3 focus:py-2 focus:text-foreground"
        >
          Skip to content
        </a>
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
          <QueryProvider>
            {children}
            <Toaster />
          </QueryProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
