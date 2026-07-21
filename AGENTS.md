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
| Background work | WorkManager + foreground service (`dataSync`) |
| FFmpeg | `dev.ffmpegkit-maintained:ffmpeg-kit-audio` |
| Whisper | OkHttp multipart → OpenAI `/v1/audio/transcriptions` |
| Skills (chat) | OkHttp JSON → OpenAI `/v1/chat/completions` (`response_format: json_object`) |
| Package / app id | `com.illyism.transcribe` |
| Min / target SDK | 26 / 35, JDK 17 |

## Layout

```
app/src/main/java/com/illyism/transcribe/
  MainActivity.kt              # Compose host + bottom nav + SAF pickers
  TranscribeApp.kt             # Application, singletons, notification channel
  data/
    SettingsRepository.kt      # Encrypted prefs (API key, chunk, parallel, raw, skillModelTier)
    TranscribeSessionStore.kt  # Selected file + progress/result across process death
    SkillRepository.kt         # Built-ins + custom skills → skills.json
    HistoryStore.kt            # Completed transcripts + cached skill results → history.json
  domain/
    FfmpegProcessor.kt         # extract / optimize / chunk
    WhisperClient.kt           # parallel-safe HTTP client
    ChatClient.kt              # chat/completions for Skills
    TranscribePipeline.kt      # end-to-end job
    UriMediaAccess.kt          # meta + fd path (no full copy)
    SrtBuilder.kt / Models.kt
    skills/
      Skill.kt                 # Skill model, inputs/outputs/exports, icon map
      BuiltInSkills.kt         # Repurpose, Study Guide, Find Highlights, Ask AI
      SkillJson.kt             # Skill ↔ JSON
      SkillRunner.kt           # Build messages + parse JSON results
  work/TranscribeWorker.kt     # WorkManager entry
  ui/
    TranscribeViewModel.kt     # Pipeline + nav + history
    skills/
      SkillsViewModel.kt       # Skill CRUD + run state
      SkillsScreen.kt / SkillEditorScreen.kt / SkillRunScreen.kt /
      SkillResultsScreen.kt / SkillPickerScreen.kt
    theme/Theme.kt             # Amber accent #E8A838 on #121212
    screens/                   # Home, History, Selected, Processing, Done, Settings
    components/Components.kt   # PrimaryButton, LabeledDropdown, …
```

## Pipeline (must match CLI intent)

1. Extract mono 16 kHz MP3 from video (FFmpeg `-vn`)
2. Optimize 1.2× (`atempo`) unless Raw mode; compress further if still > ~24 MB
3. Segment into chunks (`-f segment`)
4. Transcribe chunks with limited concurrency
5. Merge segments with speed/offset correction → write `.srt` under app external files
6. Delete temp audio/chunks in `finally`
7. On DONE, append to `HistoryStore` so Skills can reuse the transcript

Progress stages: `EXTRACTING` → `OPTIMIZING` → `CHUNKING` → `TRANSCRIBING` → `SAVING` → `DONE` / `FAILED`.

## Skills system

Declarative skills transform a finished transcript via one chat/completions call. Skills are **not** a workflow builder — name, prompt, inputs, outputs, exports, icon/color.

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
  "estimatedRuntime": "~30s"
}
```

- Built-ins: Repurpose, Study Guide, Find Highlights, Ask AI (`domain/skills/BuiltInSkills.kt`).
- Custom skills persist under `filesDir/skills.json`; import/export a single skill as `.json` via SAF.
- Run flow: Done → **Create something** → pick skill → select outputs (or Ask AI prompt) → Generate → result cards (Copy / Share / Export all).
- Runs in `viewModelScope` (not WorkManager) for v1; results cached per `(transcriptId, skillId)`.

### Skills model tiers (Sol / Terra / Luna)

GPT-5.6 family ([OpenAI models](https://developers.openai.com/api/docs/models)). Picker in Settings + skill run (slider + pill). Centralized in `SkillModelTier`:

| Tier | Model id | Role |
|------|----------|------|
| Luna | [`gpt-5.6-luna`](https://developers.openai.com/api/docs/models/gpt-5.6-luna) | Cost-sensitive, high-volume |
| Terra | [`gpt-5.6-terra`](https://developers.openai.com/api/docs/models/gpt-5.6-terra) | Default — balanced intelligence & cost |
| Sol | [`gpt-5.6-sol`](https://developers.openai.com/api/docs/models/gpt-5.6-sol) | Frontier — complex professional work (`gpt-5.6` alias) |

Transcription stays `whisper-1` (separate from skills chat).

## UI / design

- Material 3 with dynamic color (Material You) on API 31+; follows system light/dark. Amber fallback on older devices.
- Bottom nav: **Home / History / Skills** (hidden during Selected / Processing / Done / Settings / skill flows).
- One job per screen; no dashboard clutter
- Processing should show video → audio size savings when known
- Gate Start when no API key; show clear permission / network / no-key states
- Done screen: **Export** (pick txt/md/srt → save to `Downloads/Transcribe`, then auto-open Share); **Create something** for Skills; Copy text under the preview

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
