# Transcribe Android — Agent Guide

Kotlin + Jetpack Compose app for on-device transcription. Companion to `@illyism/transcribe` (CLI). Keep pipeline behavior aligned with the CLI unless the user asks otherwise.

## Product rules

- **Never upload the full video.** Extract audio on-device with FFmpeg; only optimized Whisper chunks leave the device.
- Support large local files (15GB+). Open via SAF + FFmpegKit `saf:` paths (`getSafParameterForRead`) — do **not** copy the whole file into app storage, and do **not** use `/proc/self/fd/…` (Permission denied).
- API key is required before Start; store with EncryptedSharedPreferences.
- Defaults: ~20 min chunks, max 4 parallel uploads, Whisper `whisper-1`, 1.2× speed optimize (unless Raw mode).

## Stack

| Piece | Choice |
|-------|--------|
| UI | Jetpack Compose + Material 3, dark theme |
| Navigation | Jetpack Navigation 3 (`NavDisplay` + per-tab back stacks) |
| Background work | WorkManager + foreground service (`dataSync`) |
| FFmpeg | `dev.ffmpegkit-maintained:ffmpeg-kit-audio` |
| Whisper | OkHttp multipart → OpenAI `/v1/audio/transcriptions` |
| Skills | OkHttp SSE → OpenAI `/v1/responses` (Structured Outputs + streamed reasoning summary) |
| Package / app id | `com.illyism.transcribe` |
| Min / target / compile SDK | 26 / 35 / 36, JDK 17 |

## Layout

```
app/src/main/java/com/illyism/transcribe/
  MainActivity.kt              # NavDisplay host + bottom nav + SAF pickers
  TranscribeApp.kt             # Application, singletons, notification channel
  data/
    SettingsRepository.kt      # Encrypted prefs (API key, chunk, parallel, raw, skillModelTier)
    TranscribeSessionStore.kt  # Active job only (selected + progress/error)
    SkillRepository.kt         # Built-ins + custom skills → skills.json
    HistoryStore.kt            # Transcripts + title/summary/thumb + cached skill results
  domain/
    FfmpegProcessor.kt         # extract / optimize / chunk
    WhisperClient.kt           # parallel-safe HTTP client
    ResponsesClient.kt         # /v1/responses for Skills (json_schema + reasoning)
    TranscribePipeline.kt      # end-to-end job
    UriMediaAccess.kt          # meta + fd path (no full copy)
    SrtBuilder.kt / Models.kt
    skills/
      Skill.kt                 # Skill model, inputs/outputs/exports, icon map
      BuiltInSkills.kt         # Repurpose, Study Guide, Find Highlights, Ask AI
      SkillJson.kt             # Skill ↔ JSON
      SkillRunner.kt           # instructions/input + schema + parse results
  work/TranscribeWorker.kt     # WorkManager entry
  ui/
    TranscribeViewModel.kt     # Active job + settings + history list (no nav)
    nav/
      Routes.kt                # @Serializable AppKey : NavKey (entity-ID routes)
      NavigationState.kt       # Per-tab rememberNavBackStack holder
      Navigator.kt             # navigate / goBack / replaceTop / clearToRoot / openFromDeepLink
      DeepLinks.kt             # transcribe:// URI parse + share intent
    skills/
      SkillsViewModel.kt       # Skill CRUD + run state
      SkillsScreen.kt / SkillEditorScreen.kt / SkillRunScreen.kt /
      SkillResultsScreen.kt / SkillPickerScreen.kt
    theme/Theme.kt             # Amber accent #E8A838 on #121212
    screens/                   # Home, History, Selected, Processing, Done, Settings
    components/Components.kt   # PrimaryButton, LabeledDropdown, …
```

## Navigation (Nav3)

- Composition-held back stacks via `rememberNavBackStack` (one per tab: Home / History / Skills).
- Screens are addressed by ID: `TranscriptDetail(transcriptId)`, `SkillPicker(transcriptId)`, `SkillRun(transcriptId, skillId)`, `SkillResults(transcriptId, skillId)`, `SkillEditor(skillId?)`.
- Finished transcripts load from `HistoryStore.get(id)` — not mirrored in `UiState`.
- On pipeline DONE, ViewModel appends to HistoryStore and emits `finishedTranscriptId`; UI replaces `Processing` with `TranscriptDetail(id)`.
- TranscriptDetail lists cached skill runs (`HistoryStore.listCachedSkillRuns`) under **Creations**; tap → `SkillResults(transcriptId, skillId)` (loads cache if no in-memory result).
- `TranscribeSessionStore` only holds the ephemeral active job (selected video + progress/error).
- Deep links (`MainActivity` `singleTop` + `DeepLinks.kt`): `transcribe://transcript/{id}` → History → `TranscriptDetail`; `transcribe://skill/{transcriptId}/{skillId}` → History → detail → `SkillResults` (cached result required). Missing transcript → snackbar "Transcript not found". Transcript detail copies the app-scheme URI to the clipboard (not system share — `transcribe://` only opens this app).
- **Adaptive History list-detail** (medium/expanded width): Material `ListDetailSceneStrategy` (`adaptive-navigation3:1.3.0-alpha09`, compileSdk 36–compatible) on `NavDisplay`. `History` = list pane, `TranscriptDetail` = detail pane; compact width keeps single-pane push. Selecting an item uses `Navigator.openHistoryDetail` (replace detail). Back clears detail first on tablet; DoneScreen hides back when History is wide. Home/Skills adaptive panes are out of scope for v1.

## Pipeline (must match CLI intent)

1. Extract mono 16 kHz MP3 from video (FFmpeg `-vn`)
2. Optimize 1.2× (`atempo`) unless Raw mode; compress further if still > ~24 MB
3. Segment into chunks (`-f segment`)
4. Transcribe chunks with limited concurrency
5. Merge segments with speed/offset correction → write `.srt` under app external files
6. Delete temp audio/chunks in `finally`
7. On DONE, append to `HistoryStore` so Skills can reuse the transcript
8. Best-effort **Catalog enrich**: extract a local video thumbnail (`filesDir/thumbnails/{id}.jpg`) and run hidden built-in `builtin_catalog` via `SkillRunner` / `CatalogEnricher` (`TERRA_LIGHT`) to fill `HistoryEntry.title` (≤60) + `summary` (two lines). Failures leave filename + SRT preview as fallbacks. Catalog is not listed under Skills / Creations.

Progress stages: `EXTRACTING` → `OPTIMIZING` → `CHUNKING` → `TRANSCRIBING` → `SAVING` → `DONE` / `FAILED`.

## Skills system

Declarative skills transform a finished transcript via one Responses API call (`POST /v1/responses`). Skills are **not** a workflow builder — name, prompt, inputs, outputs, exports, icon/color, optional `defaultTier`.

Request shape: `stream: true` + `instructions` (skill prompt + output contract) + `input` (metadata/transcript) + `reasoning: { effort, summary: "auto" }` + `text.format` json_schema (strict, one string property per selected output). No `max_output_tokens` (unlimited output). Do **not** put `reasoning.summary_text` in `include` (API 400). While running, SkillRunScreen shows a live Reasoning panel from `response.reasoning_summary_text.delta` when present; on `response.completed`, parse final JSON into result cards. Reasoning is best-effort — never fail a run if the summary is absent. Cancel aborts the OkHttp call.

### Declarative skill format

```json
{
  "id": "custom_…",
  "name": "Repurpose",
  "description": "…",
  "icon": "sparkles",
  "color": "#E8A838",
  "prompt": "Create platform-specific content…",
  "inputs": ["TRANSCRIPT", "SRT", "METADATA"],
  "outputs": [
    { "id": "x_thread", "label": "X thread", "type": "X_THREAD", "hint": "…" }
  ],
  "exports": ["COPY", "SHARE", "MARKDOWN"],
  "category": "CUSTOM",
  "builtIn": false,
  "estimatedRuntime": "~30s",
  "defaultTier": "TERRA_LIGHT"
}
```

- Built-ins: Repurpose (`TERRA_LIGHT`), Find Highlights (`SOL_LIGHT`), Study Guide (`SOL_MEDIUM`), Ask AI (`null` → last-used). System-only Catalog (`builtin_catalog`, `TERRA_LIGHT`) auto-runs after DONE for History title/summary — hidden from picker. See `domain/skills/BuiltInSkills.kt`.
- Custom skills persist under `filesDir/skills.json`; import/export a single skill as `.json` via SAF.
- Run flow: Done → **Create something** → pick skill → select outputs (or Ask AI prompt) → Generate → result cards (Copy / Share / Export all); collapsible **Reasoning** card when a summary is present.
- Runs in `viewModelScope` (not WorkManager) for v1; results (including reasoning) cached per `(transcriptId, skillId)` under `filesDir/skill_results/`.
- TranscriptDetail **Creations** lists those caches (e.g. `Repurpose · 2h ago`); reopen via `SkillResults` + `SkillsViewModel.loadCachedResult`.
- Skill run uses `SkillsViewModel.activeTier` (from `Skill.defaultTier` or last-used); changing the picker persists as the global last-used default. Settings still edits that global default.

### Skills model picker

GPT-5.6 family ([OpenAI models](https://developers.openai.com/api/docs/models)) plus reasoning effort ([reasoning guide](https://developers.openai.com/api/docs/guides/reasoning)). Picker in Settings + skill run: pill opens a popup slider (Fastest → Smartest) with relative **cost** (`$`…`$$$$$`). Centralized in `SkillModelTier`:

| Stop | Model id | `reasoning.effort` | Cost | Label | Role |
|------|----------|--------------------|------|-------|------|
| 5.6 Terra Light | [`gpt-5.6-terra`](https://developers.openai.com/api/docs/models/gpt-5.6-terra) | `low` | 1 | Fastest | Everyday transcript skills |
| 5.6 Sol Light | [`gpt-5.6-sol`](https://developers.openai.com/api/docs/models/gpt-5.6-sol) | `low` | 2 | Fast | Frontier, lower latency |
| 5.6 Sol Medium | [`gpt-5.6-sol`](https://developers.openai.com/api/docs/models/gpt-5.6-sol) | `medium` | 3 | Balanced | High-quality work |
| 5.6 Sol High | [`gpt-5.6-sol`](https://developers.openai.com/api/docs/models/gpt-5.6-sol) | `high` | 4 | Smart | Deeper transformations |
| 5.6 Sol Extra High | [`gpt-5.6-sol`](https://developers.openai.com/api/docs/models/gpt-5.6-sol) | `xhigh` | 5 | Smartest | Complex professional work |

Transcription stays `whisper-1` (separate from Skills).

## UI / design

- Material 3 with dynamic color (Material You) on API 31+; follows system light/dark. Amber fallback on older devices.
- Bottom nav: **Home / History / Skills** (per-tab back stacks; bar hidden on flow screens).
- History: client-side search by title / filename / summary / preview; rows show thumbnail (or file icon), title (fallback filename), optional filename under title, meta, and two-line summary (fallback SRT preview). Refreshes on tab select and when the list screen appears.
- One job per screen; no dashboard clutter
- Processing should show video → audio size savings when known
- Gate Start when no API key; show clear permission / network / no-key states
- Transcript detail (`DoneScreen` / `TranscriptDetail(id)`): **Export** (pick txt/md/srt → save to `Downloads/Transcribe`, then auto-open Share); **Create something** for Skills; **Creations** list of cached skill runs; Copy text under the preview; rename updates `HistoryStore` via `renameSrt`

## Build & install

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Open this repo root in Android Studio (not the CLI repo).

## Skills & Android CLI

Prefer official Android skills under `.agents/skills/` for platform work; use this file for app-specific rules.

Skill: `.agents/skills/android-cli/SKILL.md` — use the `android` CLI for deploy, device/UI inspect, docs search, SDK, and skill management:

```bash
android run                          # build + deploy
android layout                       # UI tree (prefer over screenshots)
android layout --diff                # only what changed
android screen capture -o screenshots/screen.png
android screen capture --annotate -o screenshots/annotated.png
android docs search <keywords>       # official developer.android.com guidance
android skills list                  # list / add official skills
android skills add --skill=<name>
```

## Debugging

Prefer `android layout` / `android screen` (see above). For app logs, still use non-blocking adb:

```bash
adb devices -l
adb logcat -d --pid=$(adb shell pidof -s com.illyism.transcribe) -t 100
```

Prefer non-blocking `logcat -d`. Visually inspect PNGs from `android screen` before acting.

## Known pitfalls

- **FFmpegKit `smartexception`**: `ffmpeg-kit-audio` needs `com.arthenica:smart-exception-java:0.2.1` (not `java9` — wrong package). Already in `app/build.gradle.kts`.
- Device `unauthorized` → user must accept USB debugging prompt.
- SAF: take persistable read permission when picking; feed the content Uri through `FFmpegKitConfig.getSafParameterForRead` (not `/proc/self/fd/…`).
- FFmpeg 8: do not use `-ac N` — use `-af aformat=channel_layouts=mono`.
- Do not use blocking `adb logcat` without `-d`.

## Out of scope (unless asked)

- Accounts, cloud library, YouTube in-app browser
- iOS / React Native
- Publishing to Play Store / signing configs beyond debug
- Pro / monetization badges for Skills
