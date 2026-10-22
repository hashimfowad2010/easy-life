import type { TaskPrompt } from "./config";

/**
 * Same layout as the phone's PromptBundle.userPrompt (app/.../core/ai/PromptBundle.kt), so Gemini and Claude see the
 * same instructions. The schema text is left out when structured outputs already enforce it.
 */
export function buildUserPrompt(
  task: TaskPrompt,
  inputText: string | null,
  params: Record<string, string>,
  includeSchema: boolean,
): string {
  const lines: string[] = [task.instruction];
  if (includeSchema && task.schema) {
    lines.push("", "Reply with JSON matching this schema:", JSON.stringify(task.schema));
  }
  const entries = Object.entries(params);
  if (entries.length > 0) {
    lines.push("", "Input parameters:", ...entries.map(([k, v]) => `- ${k}: ${v}`));
  }
  if (inputText && inputText.trim()) {
    lines.push("", "Input text:", '"""', inputText, '"""');
  }
  return lines.join("\n") + "\n";
}
