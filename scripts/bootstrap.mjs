// Creates .env from .env.example and replaces every CHANGE_ME value with a random secret.
// Safe to run again: when .env exists, only the variables it lacks are added. Existing values are never changed,
// and secret values are never printed.
import { randomBytes } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const example = join(root, ".env.example");
const target = join(root, ".env");

// URL-safe, so values are safe in .env files, JDBC settings and shell commands. 32 bytes = 256 bits.
const secret = () => randomBytes(32).toString("base64url");
const fill = (text, generated) =>
  text.replace(/^([A-Z0-9_]+)=CHANGE_ME$/gm, (_, name) => {
    generated.push(name);
    return `${name}=${secret()}`;
  });

const generated = [];

if (!existsSync(target)) {
  writeFileSync(target, fill(readFileSync(example, "utf8"), generated), { mode: 0o600 });
  console.log(`Created .env with generated values for: ${generated.join(", ")}`);
  process.exit(0);
}

// .env exists: append the variables that were added to .env.example after it was created.
const existing = readFileSync(target, "utf8");
const have = new Set([...existing.matchAll(/^([A-Z0-9_]+)=/gm)].map((m) => m[1]));
const missing = [];
let pendingComments = [];
for (const line of readFileSync(example, "utf8").split(/\r?\n/)) {
  const variable = /^([A-Z0-9_]+)=/.exec(line);
  if (variable) {
    if (!have.has(variable[1])) missing.push(...pendingComments, line);
    pendingComments = [];
  } else if (line.startsWith("#")) {
    pendingComments.push(line);
  } else {
    pendingComments = [];
  }
}

if (missing.length === 0) {
  console.log(".env is up to date; nothing to add. Delete it first to generate all-new secrets.");
  process.exit(0);
}

const addition = fill(missing.join("\n"), generated);
writeFileSync(target, `${existing.replace(/\s*$/, "\n")}\n${addition}\n`, { mode: 0o600 });
console.log(`Added missing variables to .env: ${generated.join(", ") || "(no generated values)"}`);
