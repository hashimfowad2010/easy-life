import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { Timestamp, addDoc, collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

// The privacy promises in PRIVACY.md, checked against the real rules on the Firestore emulator.
let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-easylife",
    firestore: { rules: readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8") },
  });
});
afterAll(async () => env?.cleanup());
beforeEach(async () => env.clearFirestore());

const alice = () => env.authenticatedContext("alice").firestore();
const nobody = () => env.unauthenticatedContext().firestore();

const report = (over: Record<string, unknown> = {}) => ({
  storeId: "store1",
  itemKey: "sugar",
  itemName: "Sugar 1kg",
  pricePkr: 165,
  packSize: 1,
  unit: "kg",
  reportedAt: serverTimestamp(),
  reporterAnonId: "alice",
  ...over,
});

const feedback = (over: Record<string, unknown> = {}) => ({
  anonId: "alice",
  pilotCode: "BSS-JTC",
  q1: 4,
  q2: ["electricity", "bills"],
  q3: true,
  comment: "We switch off the AC earlier now",
  lang: "ur",
  createdAt: serverTimestamp(),
  ...over,
});

const impact = (over: Record<string, unknown> = {}) => ({
  anonId: "alice",
  pilotCode: "BSS-JTC",
  event: "limit_warning_shown",
  module: "limits",
  value: null,
  detail: "WATCH",
  createdAt: Timestamp.fromDate(new Date(Date.now() - 3_600_000)),
  ...over,
});

describe("signed-out users", () => {
  it("cannot read or write anything", async () => {
    await assertFails(getDocs(collection(nobody(), "priceReports")));
    await assertFails(addDoc(collection(nobody(), "priceReports"), report()));
    await assertFails(addDoc(collection(nobody(), "feedback"), feedback()));
  });
});

describe("community prices", () => {
  it("accepts a valid anonymous price report and lets signed-in users read prices", async () => {
    await assertSucceeds(addDoc(collection(alice(), "priceReports"), report()));
    await assertSucceeds(getDocs(collection(alice(), "priceReports")));
  });

  it("rejects reports in someone else's name, silly prices, unknown units and extra fields", async () => {
    await assertFails(addDoc(collection(alice(), "priceReports"), report({ reporterAnonId: "bob" })));
    await assertFails(addDoc(collection(alice(), "priceReports"), report({ pricePkr: 0 })));
    await assertFails(addDoc(collection(alice(), "priceReports"), report({ pricePkr: 250000 })));
    await assertFails(addDoc(collection(alice(), "priceReports"), report({ unit: "tola" })));
    await assertFails(addDoc(collection(alice(), "priceReports"), report({ phone: "03001234567" })));
    await assertFails(addDoc(collection(alice(), "priceReports"), report({ reportedAt: Timestamp.fromDate(new Date(2020, 0, 1)) })));
  });

  it("never allows changing or deleting a report", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "priceReports/r1"), { ...report(), reportedAt: Timestamp.now() });
    });
    await assertFails(updateDoc(doc(alice(), "priceReports/r1"), { pricePkr: 1 }));
    await assertFails(deleteDoc(doc(alice(), "priceReports/r1")));
  });

  it("stores keep only rounded coordinates inside Pakistan", async () => {
    const store = { name: "Sample Mart", area: "Wapda Town", city: "Lahore", latRound: 31.45, lngRound: 74.27, createdAt: serverTimestamp() };
    await assertSucceeds(addDoc(collection(alice(), "stores"), store));
    await assertFails(addDoc(collection(alice(), "stores"), { ...store, latRound: 51.5 }));
    await assertFails(addDoc(collection(alice(), "stores"), { ...store, address: "House 12, Street 4" }));
  });
});

describe("impact events and feedback", () => {
  it("are create-only and nobody can read them from the app", async () => {
    await assertSucceeds(addDoc(collection(alice(), "impactEvents"), impact()));
    await assertSucceeds(addDoc(collection(alice(), "feedback"), feedback()));
    await assertFails(getDocs(collection(alice(), "impactEvents")));
    await assertFails(getDocs(collection(alice(), "feedback")));
  });

  it("reject other people's ids, out-of-range answers and long text", async () => {
    await assertFails(addDoc(collection(alice(), "impactEvents"), impact({ anonId: "bob" })));
    await assertFails(addDoc(collection(alice(), "impactEvents"), impact({ detail: "x".repeat(81) })));
    await assertFails(addDoc(collection(alice(), "feedback"), feedback({ q1: 6 })));
    await assertFails(addDoc(collection(alice(), "feedback"), feedback({ q1: 4.5 })));
    await assertFails(addDoc(collection(alice(), "feedback"), feedback({ comment: "x".repeat(501) })));
    await assertFails(addDoc(collection(alice(), "feedback"), feedback({ lang: "fr" })));
    await assertFails(addDoc(collection(alice(), "feedback"), feedback({ anonId: "bob" })));
  });
});

describe("server-only and private data", () => {
  it("price cache is readable but only the functions write it", async () => {
    await assertSucceeds(getDoc(doc(alice(), "priceCache/lahore_sugar")));
    await assertFails(setDoc(doc(alice(), "priceCache/lahore_sugar"), { results: [] }));
  });

  it("rate limits and spend are closed to the app", async () => {
    await assertFails(getDoc(doc(alice(), "rateLimits/ai_2026-10-23_alice")));
    await assertFails(setDoc(doc(alice(), "rateLimits/ai_2026-10-23_alice"), { count: 0 }));
  });

  it("financial data has no place in Firestore at all", async () => {
    for (const name of ["transactions", "bills", "sms", "users", "accounts"]) {
      await assertFails(setDoc(doc(alice(), `${name}/x`), { amount: 100 }));
      await assertFails(getDoc(doc(alice(), `${name}/x`)));
    }
  });
});
