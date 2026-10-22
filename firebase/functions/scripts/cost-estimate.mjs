// Prints the Smart AI (Claude) cost table used in the README. Run: npm run cost
// Prices are Claude API list prices (USD per million tokens, checked 2026-10-23); token counts are estimates
// for this app's prompts (system prompt ~700 tokens, cached; a 1600 px photo ~2,450 image tokens).
const PRICES = {
  "claude-haiku-5-5": { input: 0.1, output: 0.5, cacheRead: 0.01 },
  "claude-sonnet-5-5": { input: 2, output: 10, cacheRead: 0.2 },
};
const WEB_SEARCH_USD = 0.01; // USD 10 per 1,000 searches
const PKR_PER_USD = 280; // assumption for the pitch; update before presenting

const TASKS = [
  // name, model, uncached input tokens, cached input tokens, output tokens (answer + thinking), web searches
  { name: "Bill photo scan (bill_extract)", model: "claude-sonnet-5-5", input: 2900, cached: 700, output: 1500, searches: 0 },
  { name: "Meter photo (meter_read)", model: "claude-sonnet-5-5", input: 2700, cached: 700, output: 600, searches: 0 },
  { name: "Receipt photo (receipt_extract)", model: "claude-sonnet-5-5", input: 2900, cached: 700, output: 1500, searches: 0 },
  { name: "Explain a document (doc_explain)", model: "claude-sonnet-5-5", input: 3000, cached: 700, output: 2500, searches: 0 },
  { name: "Bank SMS (sms_parse)", model: "claude-haiku-5-5", input: 300, cached: 700, output: 300, searches: 0 },
  { name: "Translate a page (translate)", model: "claude-haiku-5-5", input: 1500, cached: 700, output: 1500, searches: 0 },
  { name: "Weekly summary (weekly_summary)", model: "claude-haiku-5-5", input: 800, cached: 700, output: 600, searches: 0 },
  { name: "Online prices, list of up to 8 items (shop_search)", model: "claude-sonnet-5-5", input: 30000, cached: 700, output: 2000, searches: 5 },
];

// A typical family with Smart AI on, per month. Most work still runs on the phone for free.
const MONTHLY_USE = {
  "Bill photo scan (bill_extract)": 3,
  "Meter photo (meter_read)": 6,
  "Receipt photo (receipt_extract)": 4,
  "Explain a document (doc_explain)": 3,
  "Bank SMS (sms_parse)": 10, // only messages the on-device parser could not read
  "Translate a page (translate)": 4,
  "Weekly summary (weekly_summary)": 4,
  "Online prices, list of up to 8 items (shop_search)": 1, // a shared 24 h cache serves the rest
};

function cost(t) {
  const p = PRICES[t.model];
  return (t.input * p.input + t.cached * p.cacheRead + t.output * p.output) / 1e6 + t.searches * WEB_SEARCH_USD;
}

const usd = (v) => (v < 0.01 ? `$${v.toFixed(4)}` : `$${v.toFixed(3)}`);
const pkr = (v) => `Rs ${(v * PKR_PER_USD).toFixed(v * PKR_PER_USD < 1 ? 2 : 1)}`;

console.log("| Task | Model | Per use (USD) | Per use (PKR) |");
console.log("|---|---|---|---|");
for (const t of TASKS) console.log(`| ${t.name} | ${t.model} | ${usd(cost(t))} | ${pkr(cost(t))} |`);

const perFamily = TASKS.reduce((sum, t) => sum + cost(t) * (MONTHLY_USE[t.name] ?? 0), 0);
console.log();
console.log(`Typical family per month: ${usd(perFamily)} (${pkr(perFamily)})`);
console.log(`100 families per month:   $${(perFamily * 100).toFixed(2)} (Rs ${Math.round(perFamily * 100 * PKR_PER_USD)})`);
console.log(`(PKR at ${PKR_PER_USD} per USD; token counts are estimates, check real usage in the function logs.)`);
