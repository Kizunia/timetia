# Multi-stage build of the Next.js server (ADR 0047): install and build with pnpm, then run the standalone output on a
# slim Node image as a non-root user. Build context: the repository root (the pnpm workspace and lock file live there).

FROM node:24-slim AS build
RUN npm install --global pnpm@11.28.11
WORKDIR /app
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY frontend/package.json frontend/package.json
RUN --mount=type=cache,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile
COPY frontend frontend
RUN pnpm --filter kibootia-frontend build

FROM node:24-slim AS runtime
# The server only needs `node`. Dropping the npm that ships in the base image removes its bundled (and often
# vulnerable) dependencies from the final image; Trivy gates on them in CI (ADR 0047).
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
    /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack
ENV NODE_ENV=production \
    PORT=3470 \
    HOSTNAME=0.0.0.0 \
    NEXT_TELEMETRY_DISABLED=1
WORKDIR /app
# The standalone output starts at the workspace root, so the server is frontend/server.js.
COPY --from=build --chown=node:node /app/frontend/.next/standalone ./
COPY --from=build --chown=node:node /app/frontend/.next/static ./frontend/.next/static
USER node
EXPOSE 3470
CMD ["node", "frontend/server.js"]
