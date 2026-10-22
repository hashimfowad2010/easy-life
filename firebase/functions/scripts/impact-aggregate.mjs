// Collects the anonymous impact events and feedback from all pilot phones (Firestore) into CSV files.
// Run on YOUR computer (never in GitHub Actions: artifacts of a public repo can be downloaded by anyone):
//
//   cd firebase/functions && npm ci
//   node scripts/impact-aggregate.mjs --key path/to/service-account.json --out ../../impact-results
//
// Output: impact-by-pilot.csv, feedback-by-pilot.csv, feedback-comments.csv (comments were masked on the phone).
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { cert, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

const args = Object.fromEntries(
  process.argv.slice(2).reduce((pairs, a, i, all) => (a.startsWith("--") ? [...pairs, [a.slice(2), all[i + 1]]] : pairs), []),
);
if (!args.key) {
  console.error("Usage: node scripts/impact-aggregate.mjs --key service-account.json [--out folder]");
  process.exit(1);
}
const key = JSON.parse(readFileSync(args.key, "utf8"));
initializeApp({ credential: cert(key), projectId: key.project_id });
const db = getFirestore();
const out = args.out ?? "impact-results";
mkdirSync(out, { recursive: true });

const cell = (v) => {
  const s = v === null || v === undefined ? "" : String(v);
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
};
const csv = (rows) => rows.map((r) => r.map(cell).join(",")).join("\n") + "\n";

// Impact events: count and total value per pilot code and event, plus how many phones took part.
const events = await db.collection("impactEvents").get();
const byPilot = new Map();
const phones = new Map();
for (const doc of events.docs) {
  const e = doc.data();
  const pilot = e.pilotCode || "(none)";
  const k = `${pilot}\u0000${e.module}\u0000${e.event}`;
  const row = byPilot.get(k) ?? { pilot, module: e.module, event: e.event, count: 0, total: 0, hasValue: false };
  row.count++;
  if (typeof e.value === "number") {
    row.total += e.value;
    row.hasValue = true;
  }
  byPilot.set(k, row);
  if (!phones.has(pilot)) phones.set(pilot, new Set());
  phones.get(pilot).add(e.anonId);
}
const impactRows = [["pilot_code", "phones", "module", "event", "count", "total_value"]];
for (const r of [...byPilot.values()].sort((a, b) => `${a.pilot}${a.module}${a.event}`.localeCompare(`${b.pilot}${b.module}${b.event}`))) {
  impactRows.push([r.pilot, phones.get(r.pilot).size, r.module, r.event, r.count, r.hasValue ? Math.round(r.total) : ""]);
}
writeFileSync(join(out, "impact-by-pilot.csv"), csv(impactRows));

// Feedback: average usefulness, share who changed something, and what it helped with.
const feedback = await db.collection("feedback").get();
const fb = new Map();
const comments = [["pilot_code", "usefulness_1_5", "changed_something", "helped_with", "lang", "comment"]];
for (const doc of feedback.docs) {
  const f = doc.data();
  const pilot = f.pilotCode || "(none)";
  const row = fb.get(pilot) ?? { n: 0, sum: 0, changed: 0, helped: {} };
  row.n++;
  row.sum += f.q1;
  if (f.q3) row.changed++;
  for (const h of f.q2 ?? []) row.helped[h] = (row.helped[h] ?? 0) + 1;
  fb.set(pilot, row);
  if (f.comment) comments.push([pilot, f.q1, f.q3 ? "yes" : "no", (f.q2 ?? []).join("+"), f.lang, f.comment]);
}
const helpedKeys = ["electricity", "bills", "budget", "documents", "shopping", "other"];
const fbRows = [["pilot_code", "responses", "average_usefulness", "changed_something_percent", ...helpedKeys.map((h) => `helped_${h}`)]];
for (const [pilot, r] of [...fb.entries()].sort()) {
  fbRows.push([pilot, r.n, (r.sum / r.n).toFixed(1), Math.round((100 * r.changed) / r.n), ...helpedKeys.map((h) => r.helped[h] ?? 0)]);
}
writeFileSync(join(out, "feedback-by-pilot.csv"), csv(fbRows));
writeFileSync(join(out, "feedback-comments.csv"), csv(comments));

console.log(`${events.size} impact events from ${[...phones.values()].reduce((n, s) => n + s.size, 0)} phones, ${feedback.size} feedback answers -> ${out}/`);
