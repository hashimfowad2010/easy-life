import Anthropic from "@anthropic-ai/sdk";
import { HttpsError } from "firebase-functions/v2/https";
import { describe, expect, it } from "vitest";
import { MAX_IMAGES, handleAi, parseAiRequest } from "../src/ai";
import type { AiDeps } from "../src/ai";
import { costUsd, extractJsonObject, toHttpsError } from "../src/claude";
import { MemoryQuotaStore } from "../src/quota";
import { FakeClaude, FIXED_NOW, textMessage } from "./fakes";

const JPEG = "/9j/4AAQSkZJRgABAQAAAQABAAD";
const BILL_JSON = JSON.stringify({ billType: "electricity", provider: "LESCO", unitsConsumed: 186, confidence: 0.92, uncertainFields: [] });

function deps(claude: FakeClaude, over: Partial<AiDeps["settings"]> = {}, quota = new MemoryQuotaStore()): AiDeps {
  return {
    claude,
    quota,
    settings: { enabled: true, limits: { perUserDaily: 5, globalDaily: 100 }, monthlyBudgetUsd: 25, ...over },
    now: FIXED_NOW,
  };
}

async function expectCode(p: Promise<unknown>, code: string): Promise<void> {
  await expect(p).rejects.toBeInstanceOf(HttpsError);
  await p.catch((e: HttpsError) => expect(e.code).toBe(code));
}

describe("parseAiRequest", () => {
  it("accepts the phone's request shape", () => {
    const r = parseAiRequest({ task: "bill_extract", payload: null, params: {}, imageBase64: JPEG, images: [JPEG], lang: "ur" });
    expect(r).toEqual({ task: "bill_extract", payload: null, params: {}, images: [JPEG], lang: "ur" });
  });

  it("rejects bad input", () => {
    const bad = [
      null,
      { task: "rm -rf", payload: "x" },
      { task: "shop_search", payload: "x" },
      { task: "translate" },
      { task: "translate", payload: "x".repeat(20_001) },
      { task: "bill_extract", images: Array(MAX_IMAGES + 1).fill(JPEG) },
      { task: "bill_extract", images: ["iVBORw0KGgo="] },
      { task: "translate", payload: "x", params: { "bad key!": "v" } },
      { task: "translate", payload: "x", params: { k: 5 } },
    ];
    for (const b of bad) expect(() => parseAiRequest(b), JSON.stringify(b)?.slice(0, 60)).toThrow(HttpsError);
  });
});

describe("handleAi", () => {
  it("is off by default and needs a signed-in user", async () => {
    const claude = new FakeClaude();
    await expectCode(handleAi(deps(claude), undefined, { task: "translate", payload: "hi" }), "unauthenticated");
    await expectCode(handleAi(deps(claude, { enabled: false }), "u1", { task: "translate", payload: "hi" }), "failed-precondition");
    expect(claude.calls).toHaveLength(0);
  });

  it("reads a bill photo with Sonnet 5.5, structured output, cached system prompt and refusal fallback", async () => {
    const claude = new FakeClaude(textMessage(BILL_JSON));
    const reply = await handleAi(deps(claude), "u1", { task: "bill_extract", images: [JPEG], lang: "en" });

    expect(JSON.parse(reply.json).unitsConsumed).toBe(186);
    const p = claude.calls[0] as any;
    expect(p.model).toBe("claude-sonnet-5-5");
    expect(p.fallbacks).toBe("default");
    expect(p.betas).toEqual(["server-side-fallback-2026-07-01"]);
    expect(p.system[0].cache_control).toEqual({ type: "ephemeral" });
    expect(p.output_config.effort).toBe("medium");
    expect(p.output_config.format.type).toBe("json_schema");
    expect(p.output_config.format.schema.additionalProperties).toBe(false);
    const content = p.messages[0].content;
    expect(content[0]).toEqual({ type: "image", source: { type: "base64", media_type: "image/jpeg", data: JPEG } });
    expect(content[1].text).toContain("Task: bill_extract");
    expect(content[1].text).not.toContain("Reply with JSON matching this schema");
    expect(p.thinking).toBeUndefined();
    expect(p.temperature).toBeUndefined();
  });

  it("uses Haiku 5.5 (no fallback) for simple text tasks and masks again on the server", async () => {
    const claude = new FakeClaude(textMessage('{"text":"ok","confidence":0.9}', { model: "claude-haiku-5-5" }));
    const reply = await handleAi(deps(claude), "u1", {
      task: "translate",
      payload: "Call 0300-1234567, CNIC 35202-1234567-1",
      params: { direction: "en_to_ur" },
    });
    const p = claude.calls[0] as any;
    expect(p.model).toBe("claude-haiku-5-5");
    expect(p.fallbacks).toBeUndefined();
    expect(p.output_config.effort).toBe("low");
    const text: string = p.messages[0].content.at(-1).text;
    expect(text).not.toContain("0300-1234567");
    expect(text).not.toContain("35202-1234567-1");
    expect(text).toContain("[MASKED]");
    expect(text).toContain("- language: en");
    expect(reply.maskedCount).toBe(2);
  });

  it("falls back to prompt-only JSON when the schema is rejected", async () => {
    const reject = new Anthropic.BadRequestError(400, { type: "error" }, "output_config.format: schema too complex", new Headers());
    const claude = new FakeClaude(reject, textMessage("```json\n" + BILL_JSON + "\n```"));
    const reply = await handleAi(deps(claude), "u1", { task: "bill_extract", payload: "LESCO bill text" });
    expect(JSON.parse(reply.json).provider).toBe("LESCO");
    const second = claude.calls[1] as any;
    expect(second.output_config.format).toBeUndefined();
    expect(second.messages[0].content.at(-1).text).toContain("Reply with JSON matching this schema");
  });

  it("enforces the per-user daily limit and the monthly budget", async () => {
    const quota = new MemoryQuotaStore();
    const claude = new FakeClaude(...Array(3).fill(0).map(() => textMessage(BILL_JSON)));
    const d = deps(claude, { limits: { perUserDaily: 2, globalDaily: 100 } }, quota);
    await handleAi(d, "u1", { task: "bill_extract", payload: "a" });
    await handleAi(d, "u1", { task: "bill_extract", payload: "b" });
    await expectCode(handleAi(d, "u1", { task: "bill_extract", payload: "c" }), "resource-exhausted");
    await handleAi(d, "u2", { task: "bill_extract", payload: "d" });
    expect(await quota.monthSpendUsd("2026-10")).toBeGreaterThan(0);

    quota.spend.set("2026-10", 25);
    await expectCode(handleAi(d, "u3", { task: "bill_extract", payload: "e" }), "resource-exhausted");
  });

  it("never passes on refused, cut-off or incomplete answers", async () => {
    await expectCode(handleAi(deps(new FakeClaude(textMessage("", { stop: "refusal" }))), "u1", { task: "bill_extract", payload: "x" }), "failed-precondition");
    await expectCode(handleAi(deps(new FakeClaude(textMessage('{"billType":', { stop: "max_tokens" }))), "u1", { task: "bill_extract", payload: "x" }), "internal");
    const twice = (text: string) => new FakeClaude(textMessage(text), textMessage(text));
    await expectCode(handleAi(deps(twice('{"provider":"LESCO"}')), "u1", { task: "bill_extract", payload: "x" }), "internal");
    await expectCode(handleAi(deps(twice("Sorry, no JSON here")), "u1", { task: "bill_extract", payload: "x" }), "internal");
  });

  it("retries once when the answer is not valid JSON", async () => {
    const claude = new FakeClaude(textMessage("Sorry, no JSON here"), textMessage(BILL_JSON));
    const reply = await handleAi(deps(claude), "u1", { task: "bill_extract", payload: "x" });
    expect(JSON.parse(reply.json).billType).toBe("electricity");
    expect(claude.calls).toHaveLength(2);
    expect((claude.calls[1] as any).messages[0].content.at(-1).text).toContain("Reply with JSON matching this schema");
  });

  it("maps API errors to codes the phone understands", async () => {
    const rate = new Anthropic.RateLimitError(429, { type: "error" }, "rate limited", new Headers());
    await expectCode(handleAi(deps(new FakeClaude(rate)), "u1", { task: "translate", payload: "x" }), "resource-exhausted");
    expect(toHttpsError(new Anthropic.APIConnectionError({ message: "down" })).code).toBe("unavailable");
    expect(toHttpsError(new Anthropic.APIConnectionTimeoutError()).code).toBe("deadline-exceeded");
    expect(toHttpsError(new Anthropic.AuthenticationError(401, { type: "error" }, "bad key", new Headers())).code).toBe("failed-precondition");
    expect(toHttpsError(new Error("boom")).code).toBe("internal");
  });
});

describe("helpers", () => {
  it("extracts JSON from plain, fenced or chatty text", () => {
    expect(extractJsonObject('{"a":1}')).toEqual({ a: 1 });
    expect(extractJsonObject('Here you go:\n```json\n{"a":2}\n```')).toEqual({ a: 2 });
    expect(extractJsonObject('Result {"a":{"b":3}} done')).toEqual({ a: { b: 3 } });
    expect(() => extractJsonObject("[1,2]")).toThrow(HttpsError);
  });

  it("prices a typical bill scan at list prices", () => {
    // 3,000 input + 700 cached + 800 output tokens on Sonnet 5.5.
    expect(costUsd(textMessage("{}"))).toBeCloseTo(0.006 + 0.00014 + 0.008, 6);
    expect(costUsd(textMessage("{}", { model: "claude-haiku-5-5" }))).toBeCloseTo(0.0003 + 0.000007 + 0.0004, 6);
  });
});
