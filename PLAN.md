# Easy Life — build plan

Source of truth: the build spec (v2). This file is the short working plan; `PROGRESS.md` tracks what is actually done.

## Ground rules I am following
- Free, on-device first. Gemini (free tier) and Claude are pluggable providers behind Remote Config flags, **off by default**.
- Firebase Spark only. Cloud Functions are written and tested, **not deployed**.
- GitHub Actions builds everything; every phase ends with green CI, a tagged signed Release, and `PROGRESS.md` updated.
- Fictional fixtures only. Real bills/SMS go in the gitignored `test-data/` folder later, tuned through Parser Lab.
- Priority if time runs short: Bills + Limits → Urdu Helper → Budget → Shopping.

## Key technical decisions
| Area | Decision | Why |
|---|---|---|
| Build | AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21, KSP 2.3.12, Gradle 9.8.2, compile/target SDK 37 | Latest stable on 22 Oct 2026 |
| UI text | Typed bilingual string tables in `core/i18n` (every entry has `en` + `ur`) | Guarantees no missing Urdu string; language/RTL follows the per-app locale (AppCompat) |
| DB | Room + SQLCipher (`net.zetetic:sqlcipher-android`), passphrase wrapped by an AES-GCM Android Keystore key | Current, non-deprecated approach (EncryptedSharedPreferences is deprecated) |
| Charts | Small custom Compose Canvas charts in `core/designsystem` | Only bars/lines needed; no API churn, smaller APK, RTL-safe |
| AI | `AiRouter` + providers `ON_DEVICE`, `GEMINI_FREE`, `CLAUDE`, `MOCK`; typed task specs; provenance stored on every record | Spec 4.2 |
| Cloud privacy | Text is masked on device; images sent to cloud have sensitive OCR regions blacked out first | Spec rule 4 also covers photos |
| Signing | One PKCS12 key generated once, stored as GitHub secrets, offline backup kept outside the repo | Phone accepts every update |
| Versioning | `versionCode` derived from the tag (`vA.B.C` → A·1,000,000 + B·1,000 + C) | Strictly increases with every release tag |

## Phases
0. **Foundation:** repo, CI, signed releases, theme, en/ur + RTL, Urdu fonts, onboarding, bottom bar, Modules grid, Today shell, Settings, dev tools + error report, Room + SQLCipher, Crashlytics hook, AI module (router, on-device, mock, consent), Universal Scan + on-device classification.
1. **Hero: Bills + Limits:** bill parser + fixtures, confirm screen, reminders, Limit Engine, tariff calculator + calibration, meter OCR (CameraX), dashboard, history, appliance advisor, reading-date slip, custom limits, Today cards, Parser Lab, Gemini provider behind flag.
2. **Urdu Helper:** ML Kit translation + model download, Scan & Read, Speak, TTS/STT fallbacks, share target, "Show in Urdu".
3. **Budget:** banks, SMS import + live receiver, templates + 30+ fixtures, OTP discard, SMS Lab, review inbox, exclusion + rules + self-transfer suggestions, categories, budgets, biometric lock, bill auto-match.
4. **Smart Shopping:** list parser + dictionary, community prices, retailer search buttons, unit prices, basket optimiser, budget link, sharing.
5. **Polish:** impact metrics + feedback + report, Demo mode, Cloud Functions + ClaudeProvider (not deployed), accessibility, emulator smoke test, v1.0.0, README/PRIVACY/QA.
