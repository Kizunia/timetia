import path from "node:path";

import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // A self-contained server for the container image (ADR 0047).
  output: "standalone",
  // The workspace root holds node_modules, so tracing must start there for the standalone output to be complete.
  outputFileTracingRoot: path.join(import.meta.dirname, ".."),
  poweredByHeader: false,
  // Do not write an AGENTS.md into the repository when the dev server starts.
  agentRules: false,
  reactStrictMode: true,
  // Loaded from node_modules at runtime rather than bundled.
  serverExternalPackages: ["pino"],
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
    ];
  },
};

export default nextConfig;
