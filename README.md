# Transcribe (Android)

Kotlin + Jetpack Compose app that extracts audio on-device with FFmpeg, then uploads optimized chunks to OpenAI Whisper in parallel — so 15GB+ videos never leave the phone.

Companion to the CLI: [`@illyism/transcribe`](https://github.com/Illyism/transcribe-cli).

## Screens

1. Home — choose video / API key  
2. Transcript detail — ready → working → finished (same screen; pick/share lands here)  
3. Files — searchable history index  
4. Settings — encrypted API key, chunk length, parallel uploads, raw mode  

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

- Video is opened via Storage Access Framework (`/proc/self/fd/…`) — not copied.  
- Temp audio/chunks live in app cache and are deleted after the job.  
- SRT is written to app external files (`Android/data/…/files/transcripts`) and shared via FileProvider.  
- Defaults match the CLI: ~20 min chunks, 1.2× optimize, Whisper `whisper-1`, up to 4 parallel uploads on phone.  
- FFmpeg via `dev.ffmpegkit-maintained:ffmpeg-kit-audio`.  
