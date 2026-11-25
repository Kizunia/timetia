// Runs the applications in containers: the `apps` Compose profile (ADR 0040).
//   node scripts/apps.mjs up     builds the images and starts everything
//   node scripts/apps.mjs down   stops and removes only the two application containers; the infrastructure keeps running
// `--profile apps` on its own would replace the profiles chosen in .env (for example `observability`), so this adds
// `apps` to them instead. Stop `pnpm dev` first: the containers use the same ports.
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const action = process.argv[2];
if (action !== "up" && action !== "down") {
  console.error("Usage: node scripts/apps.mjs up|down");
  process.exit(2);
}

function profilesFromEnvFile() {
  const file = join(root, ".env");
  if (!existsSync(file)) return [];
  const match = /^COMPOSE_PROFILES=(.*)$/m.exec(readFileSync(file, "utf8"));
  return match?.[1] ? match[1].split(",").map((profile) => profile.trim()).filter(Boolean) : [];
}

const profiles = new Set([...(process.env.COMPOSE_PROFILES?.split(",") ?? profilesFromEnvFile()), "apps"]);
const args = action === "up" ? ["up", "-d", "--build", "--wait"] : ["rm", "--stop", "--force", "backend", "frontend"];
const result = spawnSync("docker", ["compose", ...args], {
  cwd: root,
  stdio: "inherit",
  env: { ...process.env, COMPOSE_PROFILES: [...profiles].join(",") },
});
process.exit(result.status ?? 1);
