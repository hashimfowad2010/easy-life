// Builds the prompt bundle from /ai-prompts exactly like the Android Gradle task `bundleAiPrompts`.
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

export function buildPromptBundle(repoRoot) {
  const dir = join(repoRoot, "ai-prompts");
  const tasks = {};
  for (const file of readdirSync(join(dir, "tasks")).filter((f) => f.endsWith(".md")).sort()) {
    const name = file.slice(0, -3);
    const task = { instruction: readFileSync(join(dir, "tasks", file), "utf8").trim() };
    const schemaFile = join(dir, "schemas", `${name}.json`);
    if (existsSync(schemaFile)) task.schema = JSON.parse(readFileSync(schemaFile, "utf8"));
    tasks[name] = task;
  }
  return {
    version: readFileSync(join(dir, "VERSION"), "utf8").trim(),
    system: readFileSync(join(dir, "system.md"), "utf8").trim(),
    tasks,
  };
}
