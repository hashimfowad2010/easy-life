import Anthropic from "@anthropic-ai/sdk";
import { HttpsError } from "firebase-functions/v2/https";
import { PRICES, WEB_SEARCH_USD } from "./config";

export type CreateParams = Anthropic.Beta.Messages.MessageCreateParamsNonStreaming;
export type ClaudeMessage = Anthropic.Beta.Messages.BetaMessage;

/** The part of the Anthropic client the functions use (a fake in tests). */
export interface ClaudeClient {
  beta: { messages: { create(params: CreateParams): Promise<ClaudeMessage> } };
}

/**
 * Sonnet 5.5 can decline a request in a few safety categories; with server-side fallback the API re-runs it on a
 * suitable model inside the same call instead of failing. Haiku 5.5 has no server-side fallback.
 */
export function withFallback(params: CreateParams): CreateParams {
  if (params.model !== "claude-sonnet-5-5") return params;
  return { ...params, betas: ["server-side-fallback-2026-07-01"], fallbacks: "default" };
}

/** All text the model wrote, in order (web search answers can be split into several cited text blocks). */
export function textOf(message: ClaudeMessage): string {
  return message.content
    .filter((b): b is Anthropic.Beta.Messages.BetaTextBlock => b.type === "text")
    .map((b) => b.text)
    .join("");
}

/** Finds the JSON object in the model's text: the whole text, a ```json fence, or the outermost {...}. */
export function extractJsonObject(text: string): unknown {
  const candidates: string[] = [text.trim()];
  const fence = /```(?:json)?\s*([\s\S]*?)```/g;
  for (const m of text.matchAll(fence)) candidates.push(m[1].trim());
  const last = text.lastIndexOf("}");
  for (let i = text.indexOf("{"), tries = 0; i >= 0 && i < last && tries < 20; i = text.indexOf("{", i + 1), tries++) {
    candidates.push(text.slice(i, last + 1));
  }
  for (const c of candidates) {
    try {
      const v = JSON.parse(c);
      if (v && typeof v === "object" && !Array.isArray(v)) return v;
    } catch {
      // try the next candidate
    }
  }
  throw new HttpsError("internal", "The AI answer was not valid JSON");
}

/** Stops on answers that must not be used, before any parsing. */
export function checkStop(message: ClaudeMessage): void {
  if (message.stop_reason === "refusal") {
    throw new HttpsError("failed-precondition", "The AI declined this request");
  }
  if (message.stop_reason === "max_tokens") {
    throw new HttpsError("internal", "The AI answer was cut off");
  }
}

/** Approximate USD cost of one response at list prices (for the monthly budget guard). */
export function costUsd(message: ClaudeMessage): number {
  const price = PRICES[message.model] ?? PRICES["claude-sonnet-5-5"];
  const u = message.usage;
  const tokens =
    u.input_tokens * price.input +
    u.output_tokens * price.output +
    (u.cache_read_input_tokens ?? 0) * price.cacheRead +
    (u.cache_creation_input_tokens ?? 0) * price.cacheWrite;
  const searches = u.server_tool_use?.web_search_requests ?? 0;
  return tokens / 1_000_000 + searches * WEB_SEARCH_USD;
}

/** Maps SDK errors to callable error codes the phone understands (see ClaudeProvider.mapCode). */
export function toHttpsError(e: unknown): HttpsError {
  if (e instanceof HttpsError) return e;
  if (e instanceof Anthropic.RateLimitError) return new HttpsError("resource-exhausted", "Smart AI is busy, try again later");
  if (e instanceof Anthropic.APIConnectionTimeoutError) return new HttpsError("deadline-exceeded", "Smart AI took too long");
  if (e instanceof Anthropic.APIConnectionError) return new HttpsError("unavailable", "Smart AI could not be reached");
  if (e instanceof Anthropic.InternalServerError) return new HttpsError("unavailable", "Smart AI is temporarily unavailable");
  if (e instanceof Anthropic.AuthenticationError || e instanceof Anthropic.PermissionDeniedError) {
    return new HttpsError("failed-precondition", "Smart AI is not set up on the server");
  }
  if (e instanceof Anthropic.BadRequestError) return new HttpsError("internal", "Smart AI rejected the request");
  return new HttpsError("internal", "Smart AI failed");
}
