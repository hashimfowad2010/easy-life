# ai-prompts

The single source for every cloud "Smart AI" prompt (Gemini now if enabled, Claude after funding).

- `system.md`: shared system prompt (Pakistani context + strict JSON rules).
- `tasks/<task>.md`: the instruction for each task.
- `schemas/<task>.json`: the JSON schema the answer must match. The phone validates every cloud answer against it before use.
- `VERSION`: bump it whenever a prompt changes.

How it is used:
- **Android app:** the Gradle task `bundleAiPrompts` packs this folder into `assets/ai/prompts.json`, which is also the default value of the Remote Config key `ai_prompts`.
- **Cloud Functions:** `firebase/functions/scripts/bundle-prompts.mjs` copies the same bundle into the function at build time.

The on-device (free) provider does not use these prompts. It uses rule-based parsers in `app/.../core/parse`.
