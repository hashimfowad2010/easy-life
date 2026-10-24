# Progress

**Latest release: [v0.9.3](https://github.com/hashimfowad2010/easy-life/releases/tag/v0.9.3)** (30 MB, all five modules, Firebase connected, free Smart AI with Google Gemini). Earlier: v0.9.2 (Gemini, Firebase off), v0.9.1 (smaller download), v0.9.0 (first release, 120 MB).

Updated: 24 Oct 2026. Every item marked done is in `main`, builds in CI and passes its unit tests. The emulator smoke test walks the main screens in English and Urdu on every push.

**Firebase status:** connected since v0.9.3 (project `easy-life`, free Spark plan): anonymous sign-in, Firestore with the published rules, AI Logic (Gemini Developer API), Crashlytics. CI and release builds get `google-services.json` from the `GOOGLE_SERVICES_JSON` secret; the emulator smoke test runs without it, so tests never touch the real project.

**Smart AI = Google Gemini, free tier** (Firebase AI Logic on the Spark plan, so nothing can be billed). Users opt in (adults, 18+); photos and explanations go to Gemini, quick tasks stay on the phone, bank SMS and budget data never leave it; numbers, names and addresses are hidden first; 15 Smart AI reads per phone per day. The Claude Cloud Functions remain only as an optional paid path.

## Phase 0: Foundation ✅
Done:
- Repo, `.gitignore` (secrets, keystores, `google-services.json`, `test-data/`), CI (build, unit tests, lint, APK artifact), signed release workflow (`vA.B.C` tag → GitHub Release), versionCode from the tag.
- Signing key made once; stored as GitHub secrets; offline backup outside the repo.
- Theme (light/dark), typed English + Urdu strings, right-to-left Urdu, Noto Nastaliq / Naskh fonts, Western digits.
- Onboarding (language, modules, city + pilot code, impact opt-in), bottom bar, Modules grid, Today feed, Settings.
- Room + SQLCipher (key wrapped by Android Keystore), DataStore settings, export all data / delete all data.
- AI module: router (on-device first; Gemini and Claude behind switches; consent; masking), on-device provider, demo provider, source labels.
- Universal Scan: document scanner, gallery, share-to-app, on-device classification (bill / meter / receipt / price tag / document).
- Developer tools (tap version 7 times): Parser Lab, SMS Lab, error report, AI router view.

## Phase 1: Bills + Bijli Guard ✅
Done: on-device bill parser (fuzzy labels, OCR digit fixes, history table), confirm screen with highlighted uncertain fields, reminders (quiet hours), bill list and detail, "pay by … to save Rs …"; Shared Limit Engine, tariff calculator (both billing modes, 200/201 boundary, 6-month protected lookback, calibration), meter photo reading (CameraX) with candidates, dashboard, projections, reading-date slip warning, history, appliance advisor, custom limits, Today cards; Gemini provider (Smart AI, free tier, opt-in).

## Phase 2: Urdu Helper ✅
Done: ML Kit translation with model download (Wi-Fi by default), Scan & Read, type/paste, Speak (speech to text), read aloud with Urdu-voice guide and fallback, saved documents with dates, amounts and to-dos, reminders from documents, share target from WhatsApp/Chrome, medical/legal banner.

## Phase 3: Budget ✅
Done: bank setup (name + optional last 4 only); bank SMS shared or pasted into Budget (main `EasyLife.apk`, since v0.9.4); SMS import + live receiver and notification-access option in `EasyLife-with-SMS.apk` only (Google Play Protect blocks sideloaded apps that ask to read SMS); statement CSV import, 33 fictional SMS fixtures, OTP/security messages discarded before anything else, SMS Lab, review inbox, exclusions with undo, rules, self-transfer and refund pairing, bill auto-match, categories, budgets through the Limit Engine, biometric lock.

## Phase 4: Smart Shopping ✅
Done: Roman-Urdu/Urdu/English list parser + grocery dictionary, saved lists with "can skip" items, community prices from receipts and shelf tags (with consent), outlier filter (median ± MAD), retailer search buttons (6 verified URL patterns), typing a found price back, unit-price comparison, best single shop, best split across 2 shops, savings vs most expensive / average, budget link, share list in Urdu or English, receipt → Budget transaction + prices.

## Phase 5: Polish 🟡 (in progress)
Done:
- Impact events (anonymous, rounded, opt-in upload) including **cycles kept under the limit after a warning** and AI source + correction rate.
- **ILAP impact report** (Settings → Help the project): highlights, activity per module, results per AI source; **CSV** and **one-page PDF** export.
- **Feedback** after 3 days of use (Today card, or Settings): 3 questions in Urdu/English, sent anonymously, kept and retried when offline.
- Demo mode (fictional family: bill history crossing 200 in July, readings, transactions with an exclusion, saved shopping list, translated circular).
- **Optional paid path (not needed):** Cloud Functions `ai` and `shopSearch` (TypeScript, Claude Haiku 5.5 / Sonnet 5.5, structured JSON output, server-side masking, per-user and daily limits, monthly USD budget, web search limited to the retailer allowlist, shared 24 h price cache). 25 tests with a fake Anthropic client run in CI. **Not deployed.**
- "Check prices online" in Smart Shopping, shown only if the paid Claude path is ever switched on: results labelled Smart AI (Claude), dated, linked to the shop's page, used only after "Use this price".
- Room schema committed; CI fails on any schema change without a version bump and migration.
- Bottom bar at 200 % font: labels capped at 1.3x so icons are never cut off; smoke test now also walks Today, Bijli Guard and Settings at 2x font in Urdu.
- Firestore security rules tested on the emulator in CI (signed-out users blocked, create-only collections, no financial data, server-only collections closed).
- Remote Config template generated from the app's bundled defaults (CI checks it stays in sync); manual "Deploy Firebase" workflow.
- Script to combine pilot results from all phones into CSVs (run locally, never in public CI).
- Emulator smoke test with screenshots in English and Urdu.
- README with architecture diagram, privacy, limitations, "Switching on Smart AI (Claude)" and cost table, scaling plan.

Not done yet:
- A TalkBack walk-through on a real phone (labels were checked in code and the one unlabelled button fixed; 200 % font is checked by the emulator on Today, Bijli Guard and Settings). See QA.md.
- v1.0.0 release.

## Known issues
- Parsers are tuned on fictional samples only; real bills may need Parser Lab tuning (see `test-data.README.md`).
- On the Modules grid in Urdu the fifth tile (Smart Shopping) is below the fold on small screens; scroll down.
- Online features are off until Firebase is connected.

## What to test on your phone
Install `EasyLife.apk` from the latest release, then follow [QA.md](QA.md). Quick path (10 minutes):
1. Onboarding in اردو: the whole app flips right-to-left.
2. Today → "Try with demo data": Bijli Guard card, LESCO bill, Groceries budget, reminder.
3. Bijli Guard → read the projection; Appliances → "See saving tips".
4. Scan button → "Try a sample (fictional)" → Sample LESCO bill → Continue → check highlighted fields → Save.
5. Modules → Urdu Helper → type an English sentence → Translate → Read aloud.
6. Modules → Smart Shopping → "Weekly groceries" → Compare prices.
7. Modules → Budget → Transactions → exclude one → totals change; Undo.
8. Settings → Help the project → ILAP impact report → Save one-page PDF.
