import type { Firestore } from "firebase-admin/firestore";
import { FieldValue } from "firebase-admin/firestore";

export interface Limits {
  perUserDaily: number;
  globalDaily: number;
}

/**
 * Daily call limits and a monthly spend total, so a free/demo project can never run up a surprise bill.
 * Stored in `rateLimits` (written only by the functions; the Firestore rules close it to every app user).
 */
export interface QuotaStore {
  /** Atomically counts one call; false when this user's or everyone's daily limit for `kind` is used up. */
  tryConsume(kind: string, uid: string, day: string, limits: Limits): Promise<boolean>;
  monthSpendUsd(month: string): Promise<number>;
  addSpendUsd(month: string, usd: number): Promise<void>;
}

export class FirestoreQuotaStore implements QuotaStore {
  constructor(private readonly db: Firestore) {}

  async tryConsume(kind: string, uid: string, day: string, limits: Limits): Promise<boolean> {
    const userRef = this.db.collection("rateLimits").doc(`${kind}_${day}_${uid}`);
    const allRef = this.db.collection("rateLimits").doc(`${kind}_${day}_all`);
    return this.db.runTransaction(async (tx) => {
      const [user, all] = await Promise.all([tx.get(userRef), tx.get(allRef)]);
      const userCount = (user.get("count") as number | undefined) ?? 0;
      const allCount = (all.get("count") as number | undefined) ?? 0;
      if (userCount >= limits.perUserDaily || allCount >= limits.globalDaily) return false;
      tx.set(userRef, { count: userCount + 1, day }, { merge: true });
      tx.set(allRef, { count: allCount + 1, day }, { merge: true });
      return true;
    });
  }

  async monthSpendUsd(month: string): Promise<number> {
    const snap = await this.db.collection("rateLimits").doc(`spend_${month}`).get();
    return (snap.get("usd") as number | undefined) ?? 0;
  }

  async addSpendUsd(month: string, usd: number): Promise<void> {
    await this.db.collection("rateLimits").doc(`spend_${month}`).set({ usd: FieldValue.increment(usd), month }, { merge: true });
  }
}

/** In-memory store for tests and the local emulator. */
export class MemoryQuotaStore implements QuotaStore {
  readonly counts = new Map<string, number>();
  readonly spend = new Map<string, number>();

  async tryConsume(kind: string, uid: string, day: string, limits: Limits): Promise<boolean> {
    const userKey = `${kind}_${day}_${uid}`;
    const allKey = `${kind}_${day}_all`;
    const user = this.counts.get(userKey) ?? 0;
    const all = this.counts.get(allKey) ?? 0;
    if (user >= limits.perUserDaily || all >= limits.globalDaily) return false;
    this.counts.set(userKey, user + 1);
    this.counts.set(allKey, all + 1);
    return true;
  }

  async monthSpendUsd(month: string): Promise<number> {
    return this.spend.get(month) ?? 0;
  }

  async addSpendUsd(month: string, usd: number): Promise<void> {
    this.spend.set(month, (this.spend.get(month) ?? 0) + usd);
  }
}
