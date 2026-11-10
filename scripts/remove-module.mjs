// Removes a default-on module from a working copy: `node scripts/remove-module.mjs notes [--dry-run]`.
//
// This is the removal recipe as code (kibootia-docs/recipes/remove-notes.md). CI runs it on a fresh checkout and then
// builds and tests what is left, which is what makes the recipe verified rather than only documented (ADR 0024).
//
// A module is removed by deleting its folders and by taking its lines out of the few shared files that list modules.
// Those lines are tagged in the source, so this script never has to know the code around them:
//   - a line containing `module:<name>` is removed;
//   - everything from a line containing `module:<name>:start` to one containing `module:<name>:end` is removed,
//     both lines included.
// After that the script fails if a hard reference to the module is left (an import, a path, a permission name).
import { existsSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

/** What each removable module consists of. Paths are relative to the repository root. */
const MODULES = {
  notes: {
    directories: [
      "backend/src/main/java/com/kibootia/backend/notes",
      "backend/src/test/java/com/kibootia/backend/notes",
      "backend/src/main/resources/db/migration/notes",
      "frontend/src/features/notes",
      "frontend/src/app/notes",
    ],
    // Files matched by a name prefix inside a folder.
    prefixed: [
      { directory: "frontend/tests/unit", prefix: "notes-" },
      { directory: "frontend/tests/unit", prefix: "note-" },
      { directory: "frontend/tests/e2e", prefix: "notes." },
    ],
    // Literal text taken out of a file: [file, text].
    removals: [
      ["backend/src/main/resources/application.properties", ",classpath:db/migration/notes"],
      ["backend/src/test/resources/config/application.properties", ",classpath:db/migration/notes"],
    ],
    // Shared files that carry `module:notes` tags.
    tagged: [
      "frontend/src/modules.ts",
      "frontend/src/proxy.ts",
      "frontend/src/lib/api/problem.ts",
      "frontend/src/features/audit/AuditScreen.tsx",
    ],
    // A reference to the module that must not survive in the source of the rest of the application.
    leftovers: [
      "com.kibootia.backend.notes",
      "/api/v1/notes",
      "notes.notes.",
      "features/notes",
      "db/migration/notes",
    ],
    scan: ["backend/src", "frontend/src", "frontend/tests"],
    // Files that mention the module on purpose, because they are written to work whether or not it is there. Comments in
    // any file are never checked.
    allowed: {
      "backend/src/test/java/com/kibootia/backend/ModuleStartupIT.java": "checks that the endpoints are served or absent",
      "backend/src/test/java/com/kibootia/backend/platform/web/OpenApiContractIT.java": "leaves the module's paths out of the core list",
    },
  },
};

const args = process.argv.slice(2);
const dryRun = args.includes("--dry-run");
const name = args.find((arg) => !arg.startsWith("--"));

if (!name || !MODULES[name]) {
  console.error(`Usage: node scripts/remove-module.mjs <${Object.keys(MODULES).join("|")}> [--dry-run]`);
  process.exit(2);
}
const module = MODULES[name];

const log = (message) => console.log(`${dryRun ? "[dry run] " : ""}${message}`);
const abs = (path) => join(root, path);

/** Every file below a folder, skipping build output and dependencies. */
function* walk(directory) {
  if (!existsSync(directory)) {
    return;
  }
  for (const entry of readdirSync(directory)) {
    if (["node_modules", "target", ".next", "playwright-report", "test-results"].includes(entry)) {
      continue;
    }
    const path = join(directory, entry);
    if (statSync(path).isDirectory()) {
      yield* walk(path);
    } else {
      yield path;
    }
  }
}

function removePath(path) {
  if (!existsSync(path)) {
    log(`already gone: ${relative(root, path)}`);
    return;
  }
  log(`delete ${relative(root, path)}`);
  if (!dryRun) {
    rmSync(path, { recursive: true, force: true });
  }
}

/** Takes the tagged lines and blocks out of a file; returns how many lines went. */
function stripTagged(text) {
  const lines = text.split("\n");
  const kept = [];
  let inBlock = false;
  let removed = 0;
  for (const line of lines) {
    if (line.includes(`module:${name}:start`)) {
      inBlock = true;
    }
    if (inBlock || line.includes(`module:${name}`)) {
      removed += 1;
    } else {
      kept.push(line);
    }
    if (line.includes(`module:${name}:end`)) {
      inBlock = false;
    }
  }
  if (inBlock) {
    throw new Error(`module:${name}:start without a matching module:${name}:end`);
  }
  return { text: kept.join("\n"), removed };
}

// 1. Folders and files.
for (const directory of module.directories) {
  removePath(abs(directory));
}
for (const { directory, prefix } of module.prefixed) {
  const folder = abs(directory);
  if (existsSync(folder)) {
    for (const entry of readdirSync(folder)) {
      if (entry.startsWith(prefix)) {
        removePath(join(folder, entry));
      }
    }
  }
}

// 2. Literal text in configuration files.
for (const [file, text] of module.removals) {
  const path = abs(file);
  const content = readFileSync(path, "utf8");
  if (!content.includes(text)) {
    log(`already clean: ${file}`);
    continue;
  }
  log(`edit ${file}`);
  if (!dryRun) {
    writeFileSync(path, content.split(text).join(""), "utf8");
  }
}

// 3. Tagged lines in the shared files.
for (const file of module.tagged) {
  const path = abs(file);
  if (!existsSync(path)) {
    console.error(`Missing file: ${file}`);
    process.exit(1);
  }
  const { text, removed } = stripTagged(readFileSync(path, "utf8"));
  log(`${removed === 0 ? "already clean" : `remove ${removed} tagged line(s) from`}: ${file}`);
  if (!dryRun && removed > 0) {
    writeFileSync(path, text, "utf8");
  }
}

// 4. Nothing hard-coded may be left behind.
if (!dryRun) {
  const problems = [];
  for (const folder of module.scan) {
    for (const path of walk(abs(folder))) {
      const shown = relative(root, path).split("\\").join("/");
      if (/\.(java|ts|tsx|properties|sql|json|mjs)$/.test(path) && !path.endsWith("schema.gen.ts") && !(shown in module.allowed)) {
        // A comment may name the module; code, paths and configuration may not.
        const code = readFileSync(path, "utf8")
          .split("\n")
          .filter((line) => !/^\s*(\*|\/\*|\/\/)/.test(line))
          .join("\n");
        for (const leftover of module.leftovers) {
          if (code.includes(leftover)) {
            problems.push(`${shown} still contains "${leftover}"`);
          }
        }
      }
    }
  }
  if (problems.length > 0) {
    console.error(`The ${name} module is not fully removed:\n  ${problems.join("\n  ")}`);
    process.exit(1);
  }
  console.log(`The ${name} module is removed. Rebuild the backend (it rewrites openapi.json) and regenerate the client.`);
}
