// Starts the backend and the Next.js server on the host, with the settings from the root .env (ADR 0040).
// The infrastructure must already be running: `pnpm infra:up`. Press Ctrl+C to stop both.
import { spawn, spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const isWindows = process.platform === "win32";

const envFile = join(root, ".env");
if (!existsSync(envFile)) {
  console.error("No .env found. Run 'pnpm bootstrap' first.");
  process.exit(1);
}

/** Minimal .env reader: KEY=VALUE lines, # comments. Values are never printed. */
function readEnv(file) {
  const values = {};
  for (const line of readFileSync(file, "utf8").split(/\r?\n/)) {
    const match = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/.exec(line);
    if (match && !line.trim().startsWith("#")) values[match[1]] = match[2].replace(/^(['"])(.*)\1$/, "$2");
  }
  return values;
}

const env = {
  ...readEnv(envFile),
  ...process.env, // real environment variables win over .env, as for the backend
  // Tracing goes to the local Grafana LGTM container (ADR 0030). Exporting fails quietly when it is not running.
  OTEL_EXPORTER_OTLP_ENDPOINT: process.env.OTEL_EXPORTER_OTLP_ENDPOINT ?? "http://localhost:4318",
};

const children = [];
let stopping = false;

function stopAll(code = 0) {
  if (stopping) return;
  stopping = true;
  for (const child of children) {
    if (child.exitCode !== null || !child.pid) continue;
    if (isWindows) spawnSync("taskkill", ["/pid", String(child.pid), "/T", "/F"], { stdio: "ignore" });
    else child.kill("SIGTERM");
  }
  setTimeout(() => process.exit(code), 500);
}

function run(name, command, args, cwd) {
  // On Windows the Maven wrapper and pnpm are .cmd files, which must be started through cmd.exe. Passing the program
  // as ".\name" works even when the environment stops cmd.exe from searching the current directory.
  const [file, fileArgs] = isWindows ? ["cmd.exe", ["/d", "/s", "/c", command, ...args]] : [command, args];
  const child = spawn(file, fileArgs, { cwd, env, stdio: ["ignore", "pipe", "pipe"] });
  children.push(child);
  const prefix = (stream, target) => {
    let pending = "";
    stream.on("data", (chunk) => {
      pending += chunk.toString();
      const lines = pending.split(/\r?\n/);
      pending = lines.pop() ?? "";
      for (const line of lines) target.write(`[${name}] ${line}\n`);
    });
  };
  prefix(child.stdout, process.stdout);
  prefix(child.stderr, process.stderr);
  child.on("exit", (code) => {
    if (!stopping) {
      console.error(`[${name}] exited with code ${code}; stopping the other process.`);
      stopAll(code ?? 1);
    }
  });
}

process.on("SIGINT", () => stopAll(0));
process.on("SIGTERM", () => stopAll(0));

console.log("Starting the backend (http://localhost:8080) and the web app (http://localhost:3470)...");
run("backend", isWindows ? ".\\mvnw.cmd" : "./mvnw", ["-B", "spring-boot:run"], join(root, "backend"));
run("frontend", "pnpm", ["--dir", "frontend", "exec", "next", "dev", "--port", "3470"], root);
