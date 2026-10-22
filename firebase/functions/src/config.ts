import promptsJson from "./generated/prompts.json";
import modelsJson from "./generated/claude_models.json";
import retailersJson from "./generated/retailers.json";
import type { JsonSchema } from "./schema";

export interface TaskPrompt {
  instruction: string;
  schema?: JsonSchema;
}

export interface PromptBundle {
  version: string;
  system: string;
  tasks: Record<string, TaskPrompt>;
}

export interface Retailer {
  id: string;
  name: string;
  searchUrl: string;
  domains: string[];
  grocery: boolean;
}

/** Same bundle the phone uses (copied from /ai-prompts by scripts/bundle-prompts.mjs). */
export const PROMPTS = promptsJson as unknown as PromptBundle;
export const CLAUDE_MODELS = modelsJson as Record<string, string>;
export const RETAILERS = (retailersJson as unknown as { retailers: Retailer[] }).retailers;

export type Effort = "low" | "medium" | "high";

/** How hard each task makes the model think. Simple look-ups stay cheap; reading photos gets more care. */
export const TASK_EFFORT: Record<string, Effort> = {
  classify_image: "low",
  translate: "low",
  list_normalize: "low",
  sms_parse: "low",
  weekly_summary: "low",
  meter_read: "medium",
  bill_extract: "medium",
  receipt_extract: "medium",
  doc_explain: "medium",
  shop_search: "medium",
};

/** Output limit per task (includes the model's thinking). Everything else gets 8000. */
export const TASK_MAX_TOKENS: Record<string, number> = {
  doc_explain: 16000,
  shop_search: 16000,
};

/** Tasks the `ai` function serves. Shop search has its own function (web search + shared cache). */
export const AI_TASKS = Object.keys(PROMPTS.tasks).filter((t) => t !== "shop_search");

export function modelFor(task: string): string {
  return CLAUDE_MODELS[task] ?? CLAUDE_MODELS.default ?? "claude-haiku-5-5";
}

/**
 * List prices in USD per million tokens (Claude API, checked 2026-10-23). Used only to keep a running monthly
 * total so the function can stop before the budget is spent. Haiku 5.5 prices are for prompts under 100K tokens.
 */
export const PRICES: Record<string, { input: number; output: number; cacheRead: number; cacheWrite: number }> = {
  "claude-haiku-5-5": { input: 0.1, output: 0.5, cacheRead: 0.01, cacheWrite: 0.125 },
  "claude-sonnet-5-5": { input: 2, output: 10, cacheRead: 0.2, cacheWrite: 2.5 },
};
/** Web search: USD 10 per 1,000 searches. */
export const WEB_SEARCH_USD = 0.01;
