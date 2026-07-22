# Changelog

## Unreleased

### Changed
- Replaced Home/Files/Skills bottom navigation with a single Transcripts inbox.
- Added durable multi-file background queueing with persisted SAF access and in-card progress.
- Added typed job states, pipeline stages, queue-scoped errors, cancellation, and crash recovery.
- Made transcript detail completed-only with inline Media3 playback and tap-to-seek SRT cues.
- Added conditional batch cost confirmation and lifetime upload-avoidance estimates.
- Moved custom skill management into Settings; skills remain contextual on transcripts.

### Fixed
- Existing drafts and interrupted jobs no longer disappear from the library.
- Invalid-key and quota failures pause the queue instead of failing every queued file.
- Temporary work from crashed jobs is cleaned without deleting audio retained for `WAITING_FOR_KEY`.

All notable changes to **Transcribe Android** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project roughly follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- Unified transcription flow: pick/share opens `TranscriptDetail` immediately; Ready → Working → Complete phases on one screen (removed `Selected` / `Processing` routes).
- Share / Open-with intents for video and audio land on transcript detail with auto-start when API key is set.

### Planned

- Adaptive panes for Home / Skills (History list-detail is in)
- Accounts, cloud library, YouTube in-app browser
- Play Store publishing / release signing beyond debug

## [0.1.0] — 2026-07-21

First shipping day: companion Android app for [`@illyism/transcribe`](https://github.com/Illyism/transcribe-cli), built end-to-end in one session.

### Added

#### Core pipeline

- On-device audio extract (FFmpeg mono 16 kHz MP3), optional 1.2× optimize, chunking, parallel Whisper uploads
- WorkManager + foreground `dataSync` service for background transcription
- SAF picking for large local files (15GB+); persistable read permission
- EncryptedSharedPreferences for the OpenAI API key
- Settings: chunk length, max parallel uploads, Raw mode, skill model tier

#### Files (History)

- Local transcript library with search (title / filename / summary / preview)
- Catalog enrich after DONE: local thumbnail + hidden `builtin_catalog` skill for title (≤60) + two-line summary
- Transcript detail with Media3 source video playback when `sourceUri` is available
- Deep links: `transcribe://transcript/{id}`, `transcribe://skill/{transcriptId}/{skillId}`
- Adaptive History list-detail on medium/expanded width (`ListDetailSceneStrategy`)

#### Skills

- Declarative Skills system (built-ins + custom JSON): Repurpose, Find Highlights, Study Guide, Ask AI
- OpenAI Responses API (`/v1/responses`) with Structured Outputs and streamed reasoning summary
- GPT-5.6 Terra / Sol model picker (Fastest → Smartest, relative `$`…`$$$$$` cost)
- One-tap AssistChip run from transcript detail → streaming SkillResults; Adjust in a bottom sheet
- Cached skill results under Creations; reopen without re-running
- Skills tab for management (edit / customize / import-export)

#### Navigation & architecture

- Jetpack Navigation 3 with per-tab back stacks (Home / Files / Skills)
- Entity-ID routes (`TranscriptDetail`, `SkillResults`, `SkillEditor`)
- Ephemeral active job in `TranscribeSessionStore`; finished work in `HistoryStore`

#### Agent / tooling

- Official Android skills under `.agents/skills/`
- `AGENTS.md` product rules aligned with the CLI
- Prefer `android` CLI for deploy, layout inspect, and screen capture

### Changed

- Done screen redesigned as a post-completion launchpad (not a dead-end receipt)
- Material You dynamic color (API 31+) with amber fallback
- Export UX: primary Copy text; Download txt / md / srt; Share alongside Download
- Skill run path collapsed to chip → results (picker / run screens removed)
- Bottom nav label **Files** (route key remains History)

### Fixed

- FFmpegKit runtime crash: add `com.arthenica:smart-exception-java` (not `java9`)
- Unstick failed jobs after crash / restart
- SAF extract via FFmpegKit `saf:` paths instead of `/proc/self/fd/…` (Permission denied)
- Responses streaming: handle refusals and incomplete responses
- Transcript detail scroll / preview ordering with video player + Creations

### Day log (2026-07-21)

| Time (UTC+2) | Focus |
|--------------|--------|
| ~11:00 | Scaffold app + agent skills; fix FFmpegKit / SAF blockers |
| ~11:40 | Done screen launchpad, Material You, Download / Copy export |
| ~12:00 | Skills system + GPT-5.6 model tiers |
| ~13:00 | Responses API, reasoning UX, streaming |
| ~14:00 | Nav 3, session vs history, deep links |
| ~15:30 | Catalog thumbnails, video player, Files polish |
| ~16:00 | One-tap skill chips; remove multi-step skill run screens |

[Unreleased]: https://github.com/Illyism/transcribe-android/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Illyism/transcribe-android/releases/tag/v0.1.0
