/**
 * Easy Life Cloud Functions (spec section 9). Written and tested in CI, NOT deployed: Cloud Functions need the
 * Blaze plan and an Anthropic API key, which come with funding. Until then the app runs fully on-device.
 *
 * - `ai`: Smart AI (Claude) for one task (bill, meter, receipt, SMS, translate, explain, ...). The API key lives
 *   only here (Secret Manager), never in the app.
 * - `shopSearch`: current online grocery prices from the allowed retailer sites, cached for everyone for a day.
 *
 * Both are off until CLAUDE_ENABLED=true, need a signed-in (anonymous) user, and stop at a daily limit per user
 * and a monthly budget in USD.
 */
import Anthropic from "@anthropic-ai/sdk";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { defineBoolean, defineInt, defineSecret } from "firebase-functions/params";
import { onCall } from "firebase-functions/v2/https";
import { handleAi } from "./ai";
import type { Log } from "./ai";
import { FirestoreQuotaStore } from "./quota";
import { FirestorePriceCache, handleShopSearch } from "./shop";

const ANTHROPIC_API_KEY = defineSecret("ANTHROPIC_API_KEY");
const CLAUDE_ENABLED = defineBoolean("CLAUDE_ENABLED", { default: false });
const AI_PER_USER_DAILY = defineInt("AI_PER_USER_DAILY", { default: 30 });
const AI_GLOBAL_DAILY = defineInt("AI_GLOBAL_DAILY", { default: 3000 });
const SHOP_PER_USER_DAILY = defineInt("SHOP_PER_USER_DAILY", { default: 10 });
const SHOP_GLOBAL_DAILY = defineInt("SHOP_GLOBAL_DAILY", { default: 300 });
const MONTHLY_BUDGET_USD = defineInt("MONTHLY_BUDGET_USD", { default: 25 });
const PRICE_CACHE_HOURS = defineInt("PRICE_CACHE_HOURS", { default: 24 });

initializeApp();

let client: Anthropic | null = null;
function claude(): Anthropic {
  client ??= new Anthropic({ apiKey: ANTHROPIC_API_KEY.value(), maxRetries: 2, timeout: 90_000 });
  return client;
}

const log: Log = (level, message, data) => logger[level](message, data);

const options = {
  secrets: [ANTHROPIC_API_KEY],
  timeoutSeconds: 120,
  memory: "512MiB" as const,
  maxInstances: 5,
  // Turn on after adding App Check (Play Integrity) to the app, so only the real app can call these.
  enforceAppCheck: false,
};

export const ai = onCall(options, (request) =>
  handleAi(
    {
      claude: claude(),
      quota: new FirestoreQuotaStore(getFirestore()),
      settings: {
        enabled: CLAUDE_ENABLED.value(),
        limits: { perUserDaily: AI_PER_USER_DAILY.value(), globalDaily: AI_GLOBAL_DAILY.value() },
        monthlyBudgetUsd: MONTHLY_BUDGET_USD.value(),
      },
      log,
    },
    request.auth?.uid,
    request.data,
  ),
);

export const shopSearch = onCall({ ...options, timeoutSeconds: 300 }, (request) =>
  handleShopSearch(
    {
      claude: claude(),
      quota: new FirestoreQuotaStore(getFirestore()),
      cache: new FirestorePriceCache(getFirestore()),
      settings: {
        enabled: CLAUDE_ENABLED.value(),
        limits: { perUserDaily: SHOP_PER_USER_DAILY.value(), globalDaily: SHOP_GLOBAL_DAILY.value() },
        monthlyBudgetUsd: MONTHLY_BUDGET_USD.value(),
        cacheHours: PRICE_CACHE_HOURS.value(),
      },
      log,
    },
    request.auth?.uid,
    request.data,
  ),
);
