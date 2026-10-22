// Generates firebase/remoteconfig.template.json from the app's bundled defaults (assets/config + /ai-prompts),
// so Remote Config always starts from exactly what the app ships with.
//   node firebase/scripts/build-remoteconfig.mjs          write the template
//   node firebase/scripts/build-remoteconfig.mjs --check  fail if the committed template is out of date (CI)
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { buildPromptBundle } from "./prompt-bundle.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, "..", "..");
const config = join(repo, "app", "src", "main", "assets", "config");
const out = join(here, "..", "remoteconfig.template.json");

const json = (file) => JSON.stringify(JSON.parse(readFileSync(join(config, file), "utf8")));
const scalars = JSON.parse(readFileSync(join(config, "scalars.json"), "utf8"));

const params = {
  tariffs: [json("tariffs.json"), "JSON", "Electricity slab rates, fixed charges and taxes (with the date they were checked)."],
  ai_routing: [json("ai_routing.json"), "JSON", "Provider order per task: ON_DEVICE first, then GEMINI_FREE or CLAUDE when switched on."],
  gemini_enabled: [scalars.gemini_enabled, "BOOLEAN", "Free Gemini tier (never used for SMS/budget data). Off by default."],
  gemini_model: [scalars.gemini_model, "STRING", "Gemini model name for Firebase AI Logic."],
  claude_enabled: [scalars.claude_enabled, "BOOLEAN", "Smart AI (Claude) through the `ai` Cloud Function. Turn on only after deploying the functions."],
  claude_models: [json("claude_models.json"), "JSON", "Claude model per task (Haiku 5.5 for quick tasks, Sonnet 5.5 for photos and explanations)."],
  ai_prompts: [JSON.stringify(buildPromptBundle(repo)), "JSON", "Cloud AI prompts and JSON schemas (generated from /ai-prompts)."],
  retailers: [json("retailers.json"), "JSON", "Retailer names, search URL patterns and allowed domains for price search."],
  feature_flags: [json("feature_flags.json"), "JSON", "Switch features on or off without an app update."],
};

const template = {
  parameters: Object.fromEntries(
    Object.entries(params).map(([key, [value, valueType, description]]) => [
      key,
      { defaultValue: { value: String(value) }, valueType, description },
    ]),
  ),
};
const text = JSON.stringify(template, null, 2) + "\n";

if (process.argv.includes("--check")) {
  const current = existsSync(out) ? readFileSync(out, "utf8").replace(/\r\n/g, "\n") : "";
  if (current !== text) {
    console.error("remoteconfig.template.json is out of date: run `node firebase/scripts/build-remoteconfig.mjs` and commit.");
    process.exit(1);
  }
  console.log("remoteconfig.template.json is up to date");
} else {
  writeFileSync(out, text);
  console.log(`wrote ${out} (${Object.keys(params).length} parameters)`);
}
