import type { Metadata } from "next";

import "./globals.css";

export const metadata: Metadata = {
  title: "Kibootia",
  description: "Kibootia walking skeleton",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-white text-neutral-900 antialiased dark:bg-neutral-950 dark:text-neutral-100">
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:rounded focus:bg-white focus:px-3 focus:py-2 focus:text-neutral-900"
        >
          Skip to content
        </a>
        <main id="main" className="mx-auto max-w-2xl px-4 py-12 sm:px-6">
          {children}
        </main>
      </body>
    </html>
  );
}
