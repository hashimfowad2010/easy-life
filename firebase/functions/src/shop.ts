import Anthropic from "@anthropic-ai/sdk";
import type { Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import type { Log } from "./ai";
import { PROMPTS, RETAILERS, modelFor } from "./config";
import type { Retailer } from "./config";
import { checkStop, costUsd, extractJsonObject, toHttpsError, withFallback } from "./claude";
import type { ClaudeClient, ClaudeMessage, CreateParams } from "./claude";
import { maskForCloud } from "./mask";
import { buildUserPrompt } from "./prompt";
import type { Limits, QuotaStore } from "./quota";

export const MAX_ITEMS = 8;
export const MAX_RESULTS_PER_ITEM = 3;
const UNITS = new Set(["kg", "g", "l", "ml", "dozen", "piece", "packet"]);

export interface ShopItem {
  itemKey: string;
  name: string;
}

export interface ShopRequest {
  items: ShopItem[];
  city: string;
  retailers: Retailer[];
}

export interface ShopResult {
  item: string;
  retailer: string;
  productTitle: string;
  packSize: number | null;
  unit: string | null;
  pricePkr: number;
  /** Rs per kg (g), per litre (ml), per piece (dozen) or per packet; null without a pack size. */
  unitPrice: number | null;
  url: string;
  seenAt: string;
}

export interface CachedItem {
  itemKey: string;
  city: string;
  results: ShopResult[];
  fetchedAt: string;
}

/** Shared, read-only-for-the-app cache (`priceCache`), so one search serves every user in a city for a day. */
export interface PriceCache {
  get(city: string, itemKey: string): Promise<CachedItem | null>;
  put(entries: CachedItem[]): Promise<void>;
}

export interface ShopSettings {
  enabled: boolean;
  limits: Limits;
  monthlyBudgetUsd: number;
  cacheHours: number;
}

export interface ShopDeps {
  claude: ClaudeClient;
  quota: QuotaStore;
  cache: PriceCache;
  settings: ShopSettings;
  now?: () => Date;
  log?: Log;
}

export interface ShopReply {
  results: ShopResult[];
  notFound: string[];
  fromCache: number;
  checkedAt: string;
  source: "Smart AI (Claude) web search";
}

function invalid(message: string): never {
  throw new HttpsError("invalid-argument", message);
}

export function parseShopRequest(raw: unknown): ShopRequest {
  if (raw === null || typeof raw !== "object") invalid("Missing request");
  const data = raw as Record<string, unknown>;
  if (!Array.isArray(data.items) || data.items.length === 0 || data.items.length > MAX_ITEMS) invalid("Send 1 to 8 items");
  const seen = new Set<string>();
  const items: ShopItem[] = [];
  for (const it of data.items as unknown[]) {
    const o = (it ?? {}) as Record<string, unknown>;
    if (typeof o.itemKey !== "string" || !/^[a-z0-9_]{1,40}$/.test(o.itemKey)) invalid("Bad itemKey");
    if (typeof o.name !== "string" || o.name.trim() === "" || o.name.length > 60) invalid("Bad item name");
    if (seen.has(o.itemKey)) continue;
    seen.add(o.itemKey);
    items.push({ itemKey: o.itemKey, name: maskForCloud(o.name.trim()).text });
  }
  const city = typeof data.city === "string" && /^[A-Za-z .-]{2,40}$/.test(data.city.trim()) ? data.city.trim() : "Lahore";
  const wanted = Array.isArray(data.retailers) ? new Set(data.retailers.filter((r): r is string => typeof r === "string")) : null;
  const retailers = RETAILERS.filter((r) => r.grocery && (wanted === null || wanted.has(r.id)));
  if (retailers.length === 0) invalid("No supported retailers selected");
  return { items, city, retailers };
}

/** Lower-case host + path without query, hash or trailing slash, so the same page always compares equal. */
export function normalizeUrl(url: string): string | null {
  try {
    const u = new URL(url);
    if (u.protocol !== "https:" && u.protocol !== "http:") return null;
    return `${u.hostname.toLowerCase().replace(/^www\./, "")}${u.pathname.replace(/\/+$/, "")}`;
  } catch {
    return null;
  }
}

export function retailerForUrl(url: string, retailers: Retailer[]): Retailer | null {
  let host: string;
  try {
    host = new URL(url).hostname.toLowerCase();
  } catch {
    return null;
  }
  return retailers.find((r) => r.domains.some((d) => host === d || host.endsWith(`.${d}`))) ?? null;
}

/** Every page the web search actually returned (results and citations). Prices from any other URL are dropped. */
export function seenUrls(messages: ClaudeMessage[]): Set<string> {
  const urls = new Set<string>();
  const add = (u: unknown) => {
    const n = typeof u === "string" ? normalizeUrl(u) : null;
    if (n) urls.add(n);
  };
  for (const m of messages) {
    for (const block of m.content) {
      if (block.type === "web_search_tool_result" && Array.isArray(block.content)) {
        for (const r of block.content) add(r.url);
      }
      if (block.type === "text") {
        for (const c of block.citations ?? []) if ("url" in c) add(c.url);
      }
    }
  }
  return urls;
}

/** Same normalization as the phone's Quantity.of: g to kg, ml to litre, dozen to 12 pieces. */
export function unitPrice(pricePkr: number, packSize: number | null, unit: string | null): number | null {
  if (packSize === null || unit === null) return null;
  const qty = unit === "g" || unit === "ml" ? packSize / 1000 : unit === "dozen" ? packSize * 12 : packSize;
  return qty > 0 ? Math.round((pricePkr / qty) * 100) / 100 : null;
}

/** Keeps only honest results: an allowed retailer, a page we saw, a sane price. The server sets the date. */
export function acceptResults(
  raw: unknown,
  wanted: Set<string>,
  retailers: Retailer[],
  seen: Set<string>,
  today: string,
): ShopResult[] {
  const list = raw && typeof raw === "object" && Array.isArray((raw as { results?: unknown }).results)
    ? ((raw as { results: unknown[] }).results)
    : [];
  const out: ShopResult[] = [];
  const perItem = new Map<string, number>();
  for (const r of list) {
    const o = (r ?? {}) as Record<string, unknown>;
    if (typeof o.item !== "string" || !wanted.has(o.item)) continue;
    if (typeof o.url !== "string") continue;
    const retailer = retailerForUrl(o.url, retailers);
    const normalized = normalizeUrl(o.url);
    if (!retailer || !normalized || !seen.has(normalized)) continue;
    if (typeof o.pricePkr !== "number" || !Number.isFinite(o.pricePkr) || o.pricePkr <= 0 || o.pricePkr >= 100_000) continue;
    const count = perItem.get(o.item) ?? 0;
    if (count >= MAX_RESULTS_PER_ITEM) continue;
    perItem.set(o.item, count + 1);
    const packSize = typeof o.packSize === "number" && o.packSize > 0 && o.packSize < 1000 ? o.packSize : null;
    const unit = typeof o.unit === "string" && UNITS.has(o.unit.toLowerCase()) ? o.unit.toLowerCase() : null;
    const pricePkr = Math.round(o.pricePkr * 100) / 100;
    out.push({
      item: o.item,
      retailer: retailer.name,
      productTitle: typeof o.productTitle === "string" ? o.productTitle.slice(0, 120) : o.item,
      packSize,
      unit,
      pricePkr,
      unitPrice: unitPrice(pricePkr, packSize, unit),
      url: o.url,
      seenAt: today,
    });
  }
  return out;
}

export function buildShopParams(req: ShopRequest, items: ShopItem[], today: string): CreateParams {
  const task = PROMPTS.tasks.shop_search;
  const domains = [...new Set(req.retailers.flatMap((r) => r.domains))];
  const text = buildUserPrompt(
    task,
    JSON.stringify(items.map((i) => ({ itemKey: i.itemKey, name: i.name }))),
    {
      today,
      city: req.city,
      allowedRetailers: req.retailers.map((r) => `${r.name} (${r.domains.join(", ")})`).join("; "),
    },
    true,
  );
  return withFallback({
    model: modelFor("shop_search"),
    max_tokens: 16000,
    system: [{ type: "text", text: PROMPTS.system, cache_control: { type: "ephemeral" } }],
    messages: [{ role: "user", content: text }],
    tools: [
      {
        type: "web_search_20260209",
        name: "web_search",
        max_uses: Math.min(2 + items.length, 8),
        allowed_domains: domains,
        user_location: { type: "approximate", country: "PK", city: req.city },
      },
    ],
    output_config: { effort: "medium" },
  });
}

export async function handleShopSearch(deps: ShopDeps, uid: string | undefined, raw: unknown): Promise<ShopReply> {
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first");
  if (!deps.settings.enabled) throw new HttpsError("failed-precondition", "Online price search is switched off");
  const req = parseShopRequest(raw);
  const now = deps.now?.() ?? new Date();
  const today = now.toISOString().slice(0, 10);
  const month = today.slice(0, 7);

  // 1. Shared cache first: free, and one search serves everyone in the city.
  const maxAgeMs = deps.settings.cacheHours * 3_600_000;
  const cached: CachedItem[] = [];
  const misses: ShopItem[] = [];
  for (const item of req.items) {
    const hit = await deps.cache.get(req.city, item.itemKey);
    if (hit && now.getTime() - Date.parse(hit.fetchedAt) < maxAgeMs) cached.push(hit);
    else misses.push(item);
  }
  const retailerNames = new Set(req.retailers.map((r) => r.name));
  const fromCache = cached.flatMap((c) => c.results.filter((r) => retailerNames.has(r.retailer)));
  if (misses.length === 0) {
    return reply(fromCache, req.items, cached.length, now);
  }

  // 2. Budget and limits only apply when we actually search.
  if ((await deps.quota.monthSpendUsd(month)) >= deps.settings.monthlyBudgetUsd) {
    throw new HttpsError("resource-exhausted", "Online price search budget for this month is used up");
  }
  if (!(await deps.quota.tryConsume("shop", uid, today, deps.settings.limits))) {
    throw new HttpsError("resource-exhausted", "Daily online price search limit reached");
  }

  // 3. One Claude call with web search limited to the retailer domains. Resume if the server pauses the turn.
  const params = buildShopParams(req, misses, today);
  const messages: ClaudeMessage[] = [];
  try {
    let history = params.messages;
    let message = await deps.claude.beta.messages.create(params);
    messages.push(message);
    for (let i = 0; message.stop_reason === "pause_turn" && i < 3; i++) {
      history = [...history, { role: "assistant", content: message.content as unknown as Anthropic.Beta.Messages.BetaContentBlockParam[] }];
      message = await deps.claude.beta.messages.create({ ...params, messages: history });
      messages.push(message);
    }
  } catch (e) {
    throw toHttpsError(e);
  } finally {
    const spent = messages.reduce((sum, m) => sum + costUsd(m), 0);
    if (spent > 0) await deps.quota.addSpendUsd(month, spent);
  }

  const last = messages[messages.length - 1];
  checkStop(last);
  let fresh: ShopResult[] = [];
  if (last.stop_reason !== "pause_turn") {
    // The JSON answer is in the model's text, possibly split into several cited text blocks.
    const text = messages.flatMap((m) => m.content).filter((b) => b.type === "text").map((b) => (b as { text: string }).text).join("");
    let parsed: unknown = null;
    try {
      parsed = extractJsonObject(text);
    } catch {
      parsed = null;
    }
    fresh = acceptResults(parsed, new Set(misses.map((m) => m.itemKey)), req.retailers, seenUrls(messages), today);
  }

  // 4. Cache every searched item, found or not, so the same question is not paid for twice today.
  await deps.cache.put(
    misses.map((m) => ({
      itemKey: m.itemKey,
      city: req.city,
      results: fresh.filter((r) => r.item === m.itemKey),
      fetchedAt: now.toISOString(),
    })),
  );
  deps.log?.("info", "shop search", {
    items: misses.length,
    found: fresh.length,
    fromCache: cached.length,
    searches: messages.reduce((n, m) => n + (m.usage.server_tool_use?.web_search_requests ?? 0), 0),
    costUsd: Number(messages.reduce((sum, m) => sum + costUsd(m), 0).toFixed(4)),
  });
  return reply([...fromCache, ...fresh], req.items, cached.length, now);
}

function reply(results: ShopResult[], items: ShopItem[], fromCache: number, now: Date): ShopReply {
  const found = new Set(results.map((r) => r.item));
  return {
    results,
    notFound: items.map((i) => i.itemKey).filter((k) => !found.has(k)),
    fromCache,
    checkedAt: now.toISOString(),
    source: "Smart AI (Claude) web search",
  };
}

function cacheId(city: string, itemKey: string): string {
  return `${city.toLowerCase().replace(/[^a-z]+/g, "-")}_${itemKey}`;
}

export class FirestorePriceCache implements PriceCache {
  constructor(private readonly db: Firestore) {}

  async get(city: string, itemKey: string): Promise<CachedItem | null> {
    const snap = await this.db.collection("priceCache").doc(cacheId(city, itemKey)).get();
    return snap.exists ? (snap.data() as CachedItem) : null;
  }

  async put(entries: CachedItem[]): Promise<void> {
    const batch = this.db.batch();
    for (const e of entries) batch.set(this.db.collection("priceCache").doc(cacheId(e.city, e.itemKey)), e);
    await batch.commit();
  }
}

export class MemoryPriceCache implements PriceCache {
  readonly entries = new Map<string, CachedItem>();

  async get(city: string, itemKey: string): Promise<CachedItem | null> {
    return this.entries.get(cacheId(city, itemKey)) ?? null;
  }

  async put(entries: CachedItem[]): Promise<void> {
    for (const e of entries) this.entries.set(cacheId(e.city, e.itemKey), e);
  }
}
