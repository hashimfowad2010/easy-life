import Anthropic from "@anthropic-ai/sdk";
import { HttpsError } from "firebase-functions/v2/https";
import { AI_TASKS, PROMPTS, TASK_EFFORT, TASK_MAX_TOKENS, modelFor } from "./config";
import { checkStop, costUsd, extractJsonObject, textOf, toHttpsError, withFallback } from "./claude";
import type { ClaudeClient, ClaudeMessage, CreateParams } from "./claude";
import { maskForCloud } from "./mask";
import { buildUserPrompt } from "./prompt";
import type { Limits, QuotaStore } from "./quota";
import { hasRequiredFields, toStrictSchema } from "./schema";
import type { JsonSchema } from "./schema";

export const MAX_PAYLOAD_CHARS = 20_000;
export const MAX_IMAGES = 3;
/** About 2 MB of JPEG; the phone sends at most 1600 px at quality 80, far below this. */
export const MAX_IMAGE_BASE64_CHARS = 2_800_000;

export interface AiRequest {
  task: string;
  payload: string | null;
  params: Record<string, string>;
  images: string[];
  lang: "en" | "ur";
}

export interface AiSettings {
  enabled: boolean;
  limits: Limits;
  monthlyBudgetUsd: number;
}

export type Log = (level: "info" | "warn", message: string, data: Record<string, unknown>) => void;

export interface AiDeps {
  claude: ClaudeClient;
  quota: QuotaStore;
  settings: AiSettings;
  now?: () => Date;
  log?: Log;
}

export interface AiReply {
  /** The model's JSON answer as a string; the phone validates it against the same schema before use. */
  json: string;
  model: string;
  promptVersion: string;
  maskedCount: number;
}

function invalid(message: string): never {
  throw new HttpsError("invalid-argument", message);
}

/** Validates the callable's data exactly as FunctionsCloudAiClient sends it. Never trusts the phone. */
export function parseAiRequest(raw: unknown): AiRequest {
  if (raw === null || typeof raw !== "object") invalid("Missing request");
  const data = raw as Record<string, unknown>;

  const task = data.task;
  if (typeof task !== "string" || !AI_TASKS.includes(task)) invalid("Unknown task");

  const payload = data.payload ?? null;
  if (payload !== null && typeof payload !== "string") invalid("payload must be text");
  if (typeof payload === "string" && payload.length > MAX_PAYLOAD_CHARS) invalid("Text is too long");

  const rawImages = Array.isArray(data.images) ? data.images : typeof data.imageBase64 === "string" ? [data.imageBase64] : [];
  if (rawImages.length > MAX_IMAGES) invalid("Too many images");
  const images = rawImages.map((img) => {
    if (typeof img !== "string" || img.length === 0) invalid("Bad image");
    if (img.length > MAX_IMAGE_BASE64_CHARS) invalid("Image is too large");
    // Base64 of every JPEG starts with "/9j/" (bytes FF D8 FF). The phone only sends redacted JPEGs.
    if (!img.startsWith("/9j/")) invalid("Images must be JPEG");
    return img;
  });

  const params: Record<string, string> = {};
  if (data.params !== undefined && data.params !== null) {
    if (typeof data.params !== "object" || Array.isArray(data.params)) invalid("params must be an object");
    const entries = Object.entries(data.params as Record<string, unknown>);
    if (entries.length > 20) invalid("Too many params");
    for (const [k, v] of entries) {
      if (!/^[A-Za-z_]{1,40}$/.test(k) || typeof v !== "string" || v.length > 500) invalid("Bad param");
      params[k] = v;
    }
  }

  const lang = data.lang === "ur" ? "ur" : "en";
  if ((payload === null || payload.trim() === "") && images.length === 0) invalid("Nothing to read");
  return { task, payload, params, images, lang };
}

/** Builds the Messages API request. `structured` asks for schema-enforced JSON (structured outputs). */
export function buildAiParams(req: AiRequest, structured: boolean): CreateParams {
  const task = PROMPTS.tasks[req.task];
  const useFormat = structured && task.schema !== undefined;
  const text = buildUserPrompt(task, req.payload, { ...req.params, language: req.lang }, !useFormat);
  const content: Anthropic.Beta.Messages.BetaContentBlockParam[] = [
    ...req.images.map((data) => ({
      type: "image" as const,
      source: { type: "base64" as const, media_type: "image/jpeg" as const, data },
    })),
    { type: "text" as const, text },
  ];
  return withFallback({
    model: modelFor(req.task),
    max_tokens: TASK_MAX_TOKENS[req.task] ?? 8000,
    // The shared system prompt is identical for every call, so it is cached (about 10x cheaper on a hit).
    system: [{ type: "text", text: PROMPTS.system, cache_control: { type: "ephemeral" } }],
    messages: [{ role: "user", content }],
    output_config: {
      effort: TASK_EFFORT[req.task] ?? "medium",
      ...(useFormat ? { format: { type: "json_schema" as const, schema: toStrictSchema(task.schema) as JsonSchema } } : {}),
    },
  });
}

/** The parsed JSON answer when it is an object with the schema's required fields, else null. */
function validAnswer(message: ClaudeMessage, task: string): unknown {
  try {
    const value = extractJsonObject(textOf(message));
    return hasRequiredFields(PROMPTS.tasks[task].schema, value) ? value : null;
  } catch {
    return null;
  }
}

export async function handleAi(deps: AiDeps, uid: string | undefined, raw: unknown): Promise<AiReply> {
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first");
  if (!deps.settings.enabled) throw new HttpsError("failed-precondition", "Smart AI (Claude) is switched off");
  const parsed = parseAiRequest(raw);

  const day = (deps.now?.() ?? new Date()).toISOString().slice(0, 10);
  const month = day.slice(0, 7);
  if ((await deps.quota.monthSpendUsd(month)) >= deps.settings.monthlyBudgetUsd) {
    throw new HttpsError("resource-exhausted", "Smart AI budget for this month is used up");
  }
  if (!(await deps.quota.tryConsume("ai", uid, day, deps.settings.limits))) {
    throw new HttpsError("resource-exhausted", "Daily Smart AI limit reached");
  }

  // Second safety net: mask identifiers again on the server (the phone already did).
  let maskedCount = 0;
  const mask = (s: string) => {
    const r = maskForCloud(s);
    maskedCount += r.maskedCount;
    return r.text;
  };
  const req: AiRequest = {
    ...parsed,
    payload: parsed.payload === null ? null : mask(parsed.payload),
    params: Object.fromEntries(Object.entries(parsed.params).map(([k, v]) => [k, mask(v)])),
  };

  const create = async (structured: boolean): Promise<ClaudeMessage> => {
    const message = await deps.claude.beta.messages.create(buildAiParams(req, structured));
    await deps.quota.addSpendUsd(month, costUsd(message));
    return message;
  };

  let message: ClaudeMessage;
  try {
    message = await create(true);
  } catch (e) {
    if (!(e instanceof Anthropic.BadRequestError) || PROMPTS.tasks[req.task].schema === undefined) throw toHttpsError(e);
    // The schema was not accepted for structured outputs: ask for JSON in the prompt instead.
    deps.log?.("warn", "structured output rejected, retrying with prompt-only JSON", { task: req.task });
    try {
      message = await create(false);
    } catch (e2) {
      throw toHttpsError(e2);
    }
  }

  checkStop(message);
  let value = validAnswer(message, req.task);
  if (value === null) {
    // Retry once on an invalid answer, with the schema spelled out in the prompt.
    deps.log?.("warn", "invalid JSON answer, retrying once", { task: req.task });
    try {
      message = await create(false);
    } catch (e) {
      throw toHttpsError(e);
    }
    checkStop(message);
    value = validAnswer(message, req.task);
    if (value === null) throw new HttpsError("internal", "The AI answer was not valid JSON");
  }
  // Counts only: the content of bills, SMS and documents is never logged.
  deps.log?.("info", "ai call", {
    task: req.task,
    model: message.model,
    images: req.images.length,
    inputTokens: message.usage.input_tokens,
    outputTokens: message.usage.output_tokens,
    cacheReadTokens: message.usage.cache_read_input_tokens ?? 0,
    costUsd: Number(costUsd(message).toFixed(5)),
  });
  return { json: JSON.stringify(value), model: message.model, promptVersion: PROMPTS.version, maskedCount };
}
