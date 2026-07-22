# Transcribe (Android)

Kotlin + Jetpack Compose transcription queue. Add one or many files, leave the app,
and return to completed searchable transcripts with synchronized playback.

The full source file stays on your device. FFmpeg extracts and optimizes audio
locally; only Whisper-sized audio chunks are sent directly to OpenAI using your key.

Companion to the CLI: [`@illyism/transcribe`](https://github.com/Illyism/transcribe-cli).

## Screens

1. Transcripts — add files, search, monitor the queue, and open completed work
2. Transcript — inline playback, seekable cues, search, skills, copy, and export
3. Settings — encrypted API key, usage receipt, skill management, advanced processing

## Requirements

- Android Studio Ladybug+ / AGP 8.7  
- JDK 17  
- Android device or emulator (arm64 recommended for FFmpegKit)  
- OpenAI API key  

## Build & run

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Or open this folder in Android Studio and Run.

## Notes

- Media is opened through Storage Access Framework + FFmpegKit `saf:` paths — never copied in full.
- WorkManager owns a durable sequential queue: one active source file, up to four chunk uploads.
- Temp audio/chunks are deleted after completion, cancellation, or terminal failure. Prepared audio is retained only while waiting for a key.
- SRT is written to app external files (`Android/data/…/files/transcripts`) and shared via FileProvider.  
- Defaults match the CLI: ~20 min chunks, 1.2× optimize, Whisper `whisper-1`, up to 4 parallel uploads on phone.  
- FFmpeg via `dev.ffmpegkit-maintained:ffmpeg-kit-audio`.  
