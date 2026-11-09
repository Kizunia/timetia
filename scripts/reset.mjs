// Removes the local Docker volumes (MySQL, Keycloak's PostgreSQL, the OpenTofu state, Grafana data) and starts the
// infrastructure again, so Flyway and the one-shot OpenTofu step seed everything from scratch.
// Local data only. .env is left alone, so the generated secrets stay valid for the new volumes.
import { spawnSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");

function compose(...args) {
  const result = spawnSync("docker", ["compose", ...args], { cwd: root, stdio: "inherit" });
  if (result.status !== 0) {
    console.error(`'docker compose ${args.join(" ")}' failed (exit code ${result.status ?? "none"}).`);
    process.exit(result.status ?? 1);
  }
}

console.log("Removing the local Kibootia containers and volumes (databases, realm state, Grafana data)...");
// --profile "*" so containers and volumes of every profile (observability, apps) are included.
compose("--profile", "*", "down", "--volumes", "--remove-orphans");
console.log("Starting the infrastructure again...");
compose("up", "-d");
