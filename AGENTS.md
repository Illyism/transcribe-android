# Transcribe Android — Agent Guide

Kotlin + Jetpack Compose app for on-device transcription. Companion to `@illyism/transcribe` (CLI). Keep pipeline behavior aligned with the CLI unless the user asks otherwise.

## Product rules

- **Never upload the full video.** Extract audio on-device with FFmpeg; only optimized Whisper chunks leave the device.
- Support large local files (15GB+). Open via SAF + `/proc/self/fd/…` — do **not** copy the whole file into app storage.
- API key is required before Start; store with EncryptedSharedPreferences.
- Defaults: ~20 min chunks, max 4 parallel uploads, Whisper `whisper-1`, 1.2× speed optimize (unless Raw mode).

## Stack

| Piece | Choice |
|-------|--------|
| UI | Jetpack Compose + Material 3, dark theme |
| Background work | WorkManager + foreground service (`dataSync`) |
| FFmpeg | `dev.ffmpegkit-maintained:ffmpeg-kit-audio` |
| Whisper | OkHttp multipart → OpenAI `/v1/audio/transcriptions` |
| Package / app id | `com.illyism.transcribe` |
| Min / target SDK | 26 / 35, JDK 17 |

## Layout

```
app/src/main/java/com/illyism/transcribe/
  MainActivity.kt              # Compose host + SAF picker
  TranscribeApp.kt             # Application, notification channel
  data/
    SettingsRepository.kt      # Encrypted prefs (API key, chunk, parallel, raw)
    TranscribeSessionStore.kt  # Selected file + progress/result across process death
  domain/
    FfmpegProcessor.kt         # extract / optimize / chunk
    WhisperClient.kt           # parallel-safe HTTP client
    TranscribePipeline.kt      # end-to-end job
    UriMediaAccess.kt          # meta + fd path (no full copy)
    SrtBuilder.kt / Models.kt
  work/TranscribeWorker.kt     # WorkManager entry
  ui/
    TranscribeViewModel.kt
    theme/Theme.kt             # Amber accent #E8A838 on #121212
    screens/                   # Home, Selected, Processing, Done, Settings
    components/Components.kt
```

## Pipeline (must match CLI intent)

1. Extract mono 16 kHz MP3 from video (FFmpeg `-vn`)
2. Optimize 1.2× (`atempo`) unless Raw mode; compress further if still > ~24 MB
3. Segment into chunks (`-f segment`)
4. Transcribe chunks with limited concurrency
5. Merge segments with speed/offset correction → write `.srt` under app external files
6. Delete temp audio/chunks in `finally`

Progress stages: `EXTRACTING` → `OPTIMIZING` → `CHUNKING` → `TRANSCRIBING` → `SAVING` → `DONE` / `FAILED`.

## UI / design

- Dark-only: background `#121212`, surface `#1A1A1A`, accent amber `#E8A838`
- One job per screen; no dashboard clutter
- Processing should show video → audio size savings when known
- Gate Start when no API key; show clear permission / network / no-key states

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

- **FFmpegKit `smartexception`**: if runtime fails with `Failed resolution of: Lcom/arthenica/smartexception/java/Exceptions`, add the missing smart-exception dependency (or switch FFmpegKit artifact) — do not ignore.
- Device `unauthorized` → user must accept USB debugging prompt.
- SAF persistable permission: take read permission when picking; keep PFD open for the whole FFmpeg extract.
- Do not use blocking `adb logcat` without `-d`.

## Out of scope (unless asked)

- Accounts, cloud library, YouTube in-app browser
- iOS / React Native
- Publishing to Play Store / signing configs beyond debug
