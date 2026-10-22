# Easy Life — conventions for Claude Code (and humans)

## Build and test
- **No local Android build.** Push to GitHub; Actions builds. Check runs with `gh run list -L 3`, `gh run watch <id>`, `gh run view <id> --log-failed`. A red build is never "done".
- CI (`.github/workflows/ci.yml`): `./gradlew testDebugUnitTest assembleDebug lintDebug` + Cloud Functions `npm ci && npm run build && npm test`.
- Release (`.github/workflows/release.yml`): push a tag `vA.B.C` → signed APK on a GitHub Release (`EasyLife-vA.B.C.apk` and `EasyLife.apk`). `versionCode = A*1_000_000 + B*1_000 + C`.
- Release notes: `release-notes/<tag>.md` (must contain "What to test on your phone").
- Toolchain: AGP 9.4 (built-in Kotlin — no `kotlin-android` plugin), Kotlin 2.4, KSP 2.3, Gradle 9.8, JDK 21 in CI, compile/target SDK 37, minSdk 26.

## Code layout (package-by-feature, one Gradle module)
```
com.easylife.app
├─ core/ai         AiRouter, providers (ON_DEVICE, GEMINI_FREE, CLAUDE, MOCK), task schemas, Masker, CloudJson, Provenance
├─ core/ocr        OcrDocument (pure), ML Kit OcrEngine
├─ core/parse      pure on-device parsers: Bill, MeterReading, Receipt, Sms, ShoppingList (+GroceryDictionary), DocInsights, ImageClassifier
├─ core/limits     Shared Limit Engine, tariffs, protected status, appliance advisor, MeterDashboard (all pure, unit-tested)
├─ core/data       Room+SQLCipher (db/), DataStore prefs, repositories, DataManager (export/delete)
├─ core/cloud      FirebaseGateway, Remote Config (AppConfig + parser)
├─ core/i18n       Txt (en+ur), Fmt (numbers/dates/Rs, bidi isolates), LanguageManager, string tables (S*)
├─ core/designsystem theme, typography (Nastaliq/Naskh), components, charts, pickers
├─ core/scan       ScanDraftStore, document scanner launchers, ScanKind routing
├─ core/notify     channels, Notifier, AlertEngine, workers
├─ core/impact     ImpactTracker, ImpactUploader
├─ core/devtools   ErrorLog, Parser Lab, SMS Lab, error report, router view
├─ feature/*       onboarding, today, modules, limits, bills, budget, urdu, shopping, settings, scan
└─ navigation      type-safe routes, root scaffold, bottom bar
```

## Rules that are product requirements
- **All UI text is a `Txt(en, ur)`** in `core/i18n/*Strings.kt`; use `.t()` in Compose. No hard-coded user-visible strings.
- Numbers: always Western digits via `Fmt`. In Urdu text, wrap numeric runs with `Fmt.ltr(..., lang)`.
- Every AI result shows its `Provenance` (`SourceChip`). Demo rows have `isDemo = true` and show `DemoChip`.
- Every extracted number goes through a confirm screen; uncertain fields are highlighted (`ConfirmField(uncertain = true)`).
- Text is masked (`Masker.forCloud`) before any cloud call; images are redacted (`CloudImagePreparer`). SMS/budget tasks are `sensitive` and never go to `GEMINI_FREE` (router-enforced and tested).
- Permissions are requested just-in-time with a reason; every flow has a "type it instead" path.
- Never commit secrets: no keystore, passwords, `google-services.json`, real bills/SMS. `test-data/` is gitignored.
- Don't write raw invisible Unicode in source; use `Char(0x2066)` style.
- Kotlin block comments nest: never write `/*` inside a KDoc (e.g. a glob like `*.json`).

## Tests
- Pure logic lives in `core/limits`, `core/parse`, `core/ai` and is unit-tested in `app/src/test`.
- Fixtures (all fictional) in `app/src/test/resources/fixtures/`. Demo samples (copies) in `app/src/main/assets/demo/`.

## Remote Config keys (bundled defaults in `app/src/main/assets/config/` and `assets/ai/prompts.json`)
`tariffs`, `ai_routing`, `gemini_enabled` (false), `gemini_model`, `claude_enabled` (false), `claude_models`, `ai_prompts`, `retailers`, `feature_flags`.
Prompts live in `/ai-prompts` and are bundled by the Gradle task `bundleAiPrompts`.
