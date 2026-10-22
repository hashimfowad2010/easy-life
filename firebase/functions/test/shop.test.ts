import { HttpsError } from "firebase-functions/v2/https";
import { describe, expect, it } from "vitest";
import { MemoryQuotaStore } from "../src/quota";
import { MemoryPriceCache, handleShopSearch, normalizeUrl, parseShopRequest } from "../src/shop";
import type { ShopDeps } from "../src/shop";
import { FakeClaude, FIXED_NOW, message } from "./fakes";

const SUGAR_URL = "https://alfatah.pk/products/sugar-1kg";
const OIL_URL = "https://www.naheed.pk/dalda-cooking-oil-5l";

/** What a web-search answer looks like: a search, its results, then the JSON answer as text. */
function searchReply(answer: object, urls: string[], opts: { stop?: string } = {}) {
  return message(
    [
      { type: "server_tool_use", id: "srvtoolu_1", name: "web_search", input: { query: "sugar price alfatah" } },
      {
        type: "web_search_tool_result",
        tool_use_id: "srvtoolu_1",
        content: urls.map((url) => ({ type: "web_search_result", url, title: "Product", encrypted_content: "x", page_age: null })),
      },
      { type: "text", text: JSON.stringify(answer), citations: null },
    ],
    { searches: 2, input: 25_000, output: 1_500, stop: opts.stop },
  );
}

function deps(claude: FakeClaude, cache = new MemoryPriceCache(), quota = new MemoryQuotaStore()): ShopDeps {
  return {
    claude,
    quota,
    cache,
    settings: { enabled: true, limits: { perUserDaily: 3, globalDaily: 50 }, monthlyBudgetUsd: 25, cacheHours: 24 },
    now: FIXED_NOW,
  };
}

const REQUEST = {
  items: [
    { itemKey: "sugar", name: "Sugar 1kg" },
    { itemKey: "cooking_oil", name: "Cooking oil 5 litre" },
    { itemKey: "eggs", name: "Eggs 1 dozen" },
  ],
  city: "Lahore",
};

describe("parseShopRequest", () => {
  it("validates items and keeps only grocery retailers", () => {
    const r = parseShopRequest({ items: [{ itemKey: "sugar", name: "Cheeni" }, { itemKey: "sugar", name: "dup" }], city: "Karachi" });
    expect(r.items).toEqual([{ itemKey: "sugar", name: "Cheeni" }]);
    expect(r.city).toBe("Karachi");
    expect(r.retailers.every((x) => x.grocery)).toBe(true);
    expect(r.retailers.map((x) => x.id)).not.toContain("priceoye");
    expect(parseShopRequest({ items: [{ itemKey: "sugar", name: "x" }], city: "<script>" }).city).toBe("Lahore");
    for (const bad of [{}, { items: [] }, { items: Array(9).fill({ itemKey: "a", name: "a" }) }, { items: [{ itemKey: "Bad Key", name: "x" }] }]) {
      expect(() => parseShopRequest(bad)).toThrow(HttpsError);
    }
    expect(() => parseShopRequest({ items: [{ itemKey: "sugar", name: "x" }], retailers: ["priceoye"] })).toThrow(HttpsError);
  });

  it("normalizes URLs for comparison", () => {
    expect(normalizeUrl("https://www.Naheed.pk/a/b/?x=1#y")).toBe("naheed.pk/a/b");
    expect(normalizeUrl("javascript:alert(1)")).toBeNull();
  });
});

describe("handleShopSearch", () => {
  it("searches only the retailer domains and keeps only honest results", async () => {
    const answer = {
      results: [
        { item: "sugar", retailer: "Al-Fatah", productTitle: "Sugar 1kg", packSize: 1, unit: "kg", pricePkr: 165, url: SUGAR_URL, seenAt: "2020-01-01" },
        { item: "cooking_oil", retailer: "Naheed", productTitle: "Dalda 5L", packSize: 5, unit: "l", pricePkr: 2950, url: OIL_URL, seenAt: "x" },
        // Invented: a page the search never returned.
        { item: "eggs", retailer: "Al-Fatah", productTitle: "Eggs", packSize: 12, unit: "piece", pricePkr: 390, url: "https://alfatah.pk/eggs", seenAt: "x" },
        // Not an allowed retailer.
        { item: "sugar", retailer: "Other", productTitle: "Sugar", packSize: 1, unit: "kg", pricePkr: 150, url: "https://example.com/sugar", seenAt: "x" },
        // Silly price.
        { item: "sugar", retailer: "Al-Fatah", productTitle: "Sugar", packSize: 1, unit: "kg", pricePkr: 0, url: SUGAR_URL, seenAt: "x" },
        // Item nobody asked for.
        { item: "gold", retailer: "Al-Fatah", productTitle: "Gold", pricePkr: 50, url: SUGAR_URL, seenAt: "x" },
      ],
      notFound: ["eggs"],
    };
    const claude = new FakeClaude(searchReply(answer, [SUGAR_URL, `${OIL_URL}?ref=search`, "https://example.com/sugar"]));
    const reply = await handleShopSearch(deps(claude), "u1", REQUEST);

    expect(reply.results.map((r) => [r.item, r.retailer, r.pricePkr])).toEqual([
      ["sugar", "Al-Fatah", 165],
      ["cooking_oil", "Naheed", 2950],
    ]);
    expect(reply.results.every((r) => r.seenAt === "2026-10-23")).toBe(true);
    expect(reply.results[1].unitPrice).toBe(590);
    expect(reply.notFound).toEqual(["eggs"]);
    expect(reply.source).toBe("Smart AI (Claude) web search");

    const p = claude.calls[0] as any;
    expect(p.model).toBe("claude-sonnet-5-5");
    expect(p.tools[0].type).toBe("web_search_20260209");
    expect(p.tools[0].allowed_domains).toEqual(expect.arrayContaining(["alfatah.pk", "naheed.pk", "daraz.pk"]));
    expect(p.tools[0].allowed_domains).not.toContain("priceoye.pk");
    expect(p.tools[0].user_location).toEqual({ type: "approximate", country: "PK", city: "Lahore" });
    expect(p.messages[0].content).toContain("Never invent or estimate a price");
  });

  it("serves the shared cache for a day without calling Claude again", async () => {
    const cache = new MemoryPriceCache();
    const answer = { results: [{ item: "sugar", retailer: "Al-Fatah", productTitle: "Sugar 1kg", packSize: 1, unit: "kg", pricePkr: 165, url: SUGAR_URL }] };
    const claude = new FakeClaude(searchReply(answer, [SUGAR_URL]));
    await handleShopSearch(deps(claude, cache), "u1", REQUEST);
    expect(cache.entries.size).toBe(3);

    const second = await handleShopSearch(deps(claude, cache), "u2", REQUEST);
    expect(claude.calls).toHaveLength(1);
    expect(second.fromCache).toBe(3);
    expect(second.results.map((r) => r.item)).toEqual(["sugar"]);
    expect(second.notFound).toEqual(["cooking_oil", "eggs"]);
  });

  it("resumes a paused turn and gives up honestly if it never finishes", async () => {
    const paused = message([{ type: "server_tool_use", id: "s1", name: "web_search", input: {} }], { stop: "pause_turn", searches: 1 });
    const claude = new FakeClaude(paused, paused, paused, paused);
    const reply = await handleShopSearch(deps(claude), "u1", { items: [{ itemKey: "sugar", name: "Sugar" }] });
    expect(claude.calls).toHaveLength(4);
    expect((claude.calls[1] as any).messages).toHaveLength(2);
    expect(reply.results).toEqual([]);
    expect(reply.notFound).toEqual(["sugar"]);
  });

  it("enforces limits and records the cost of searches", async () => {
    const quota = new MemoryQuotaStore();
    const replies = Array(4).fill(0).map(() => searchReply({ results: [] }, []));
    const claude = new FakeClaude(...replies);
    const d = { ...deps(claude, new MemoryPriceCache(), quota), settings: { ...deps(claude).settings, cacheHours: 0 } };
    for (let i = 0; i < 3; i++) await handleShopSearch(d, "u1", { items: [{ itemKey: "sugar", name: "Sugar" }] });
    await expect(handleShopSearch(d, "u1", { items: [{ itemKey: "sugar", name: "Sugar" }] })).rejects.toMatchObject({ code: "resource-exhausted" });
    // 3 calls x (25,000 in + 700 cached + 1,500 out on Sonnet 5.5 + 2 searches)
    expect(await quota.monthSpendUsd("2026-10")).toBeCloseTo(3 * (0.05 + 0.00014 + 0.015 + 0.02), 4);
  });

  it("is off unless enabled and needs a signed-in user", async () => {
    const claude = new FakeClaude();
    await expect(handleShopSearch(deps(claude), undefined, REQUEST)).rejects.toMatchObject({ code: "unauthenticated" });
    const off = { ...deps(claude), settings: { ...deps(claude).settings, enabled: false } };
    await expect(handleShopSearch(off, "u1", REQUEST)).rejects.toMatchObject({ code: "failed-precondition" });
    expect(claude.calls).toHaveLength(0);
  });
});

describe("unitPrice", () => {
  it("normalizes to per kg, litre, piece or packet", async () => {
    const { unitPrice } = await import("../src/shop");
    expect(unitPrice(860, 450, "g")).toBe(1911.11);
    expect(unitPrice(2950, 5, "l")).toBe(590);
    expect(unitPrice(390, 1, "dozen")).toBe(32.5);
    expect(unitPrice(230, 1, "packet")).toBe(230);
    expect(unitPrice(230, null, "kg")).toBeNull();
  });
});
