// Copies the shared prompt bundle and config into src/generated/ so the functions use exactly the same
// prompts, schemas, model choices and retailer list as the Android app (see /ai-prompts/README.md).
// Runs before every build and test (npm run bundle).
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { buildPromptBundle } from "../../scripts/prompt-bundle.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, "..", "..", "..");
const configDir = join(repo, "app", "src", "main", "assets", "config");
const out = join(here, "..", "src", "generated");
mkdirSync(out, { recursive: true });

const bundle = buildPromptBundle(repo);
writeFileSync(join(out, "prompts.json"), JSON.stringify(bundle, null, 2));
for (const name of ["claude_models.json", "retailers.json"]) {
  writeFileSync(join(out, name), readFileSync(join(configDir, name), "utf8"));
}
console.log(`bundled prompts ${bundle.version} (${Object.keys(bundle.tasks).length} tasks) into src/generated/`);
