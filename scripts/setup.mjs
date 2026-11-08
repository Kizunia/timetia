// Creates .env from .env.example and replaces every CHANGE_ME value with a random secret.
// Never overwrites an existing .env, and never prints secret values.
import { randomBytes } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const example = join(root, ".env.example");
const target = join(root, ".env");

if (existsSync(target)) {
  console.log(".env already exists; leaving it unchanged. Delete it first to generate new secrets.");
  process.exit(0);
}

// URL-safe, so values are safe in .env files, JDBC settings and shell commands.
const secret = () => randomBytes(24).toString("base64url");

const generated = [];
const content = readFileSync(example, "utf8").replace(/^([A-Z0-9_]+)=CHANGE_ME$/gm, (_, name) => {
  generated.push(name);
  return `${name}=${secret()}`;
});

writeFileSync(target, content, { mode: 0o600 });
console.log(`Created .env with generated values for: ${generated.join(", ")}`);
