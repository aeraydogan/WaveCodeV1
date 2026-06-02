# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**VoiceWave / WaveCode** — An Android app that records audio, generates a tattoo-friendly visual code (called a **WaveCode**), and later scans that code to play the original audio.

### Critical product rule

A waveform image alone cannot reconstruct audio. The tattoo encodes a short `publicCode` (e.g. `A7K29XQ4`) which is resolved to an audio file via a backend lookup. The visual never stores audio data itself.

### WaveCode visual format

- Looks like a stylized waveform / Spotify-code-like vertical bar pattern
- Black and white, high contrast, tattoo-friendly
- **Not** a QR code. **Not** a standard barcode.
- Custom format name: **WaveCode**

### Visual design directions

When improving the WaveCode visual, always work within a named design direction. Do not mix directions unless explicitly approved.

**Direction 1 — Spotify-like clean style** *(current approved direction)*
- Clean, minimal, premium floating capsule bar style
- Inspired by Spotify-code-like vertical rhythm — not copying Spotify's exact format
- Black rounded capsule bars on white background
- Bars are **vertically centered** on the canvas — each bar floats in the middle, not anchored to any edge
- Bar heights vary by encoded value; shorter bars appear as small centered capsules, taller bars fill more of the vertical space
- **No baseline stroke** — bars float freely with no connecting line beneath them
- Spacing is balanced and readable
- No decorative outer shell
- No bottom-anchored / baseline-anchored layout
- No center-symmetric waveform (bars are not mirrored top/bottom)
- No chaotic artistic shapes
- Priority: readability, tattoo-friendliness, clean product look

### WaveCode v1 scope

1. Encode a short `publicCode` string into a WaveCode image
2. Render WaveCode as a waveform-like vertical bar visual
3. Encoder and decoder are modular/separate classes
4. **First**: encoder + preview screen
5. **Later** (out of scope now): decoder, camera scan, audio recording, backend

## Build & Run Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device/emulator
./gradlew installDebug

# Run unit tests (JVM)
./gradlew test

# Run a single unit test class
./gradlew test --tests "com.nandroid.wavecodev1.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Lint
./gradlew lint

# Clean build
./gradlew clean
```

## Architecture & Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (migrating from View-based template)
- **Pattern**: MVVM where it adds real value; avoid over-engineering
- **Package**: `com.nandroid.wavecodev1`
- **Min SDK**: 24 | **Target/Compile SDK**: 36
- **AGP**: 9.1.1 | **Gradle**: 9.3.1

### Planned package layout (inside `com.nandroid.wavecodev1`)

```
wavecode/
  WaveCodeEncoder.kt   — publicCode → WaveCode bitmap (modular, reusable)
  WaveCodeDecoder.kt   — bitmap → publicCode (added later)
ui/
  preview/
    WaveCodePreviewScreen.kt   — Compose screen
    WaveCodePreviewViewModel.kt
```

## Dependency Management

All versions live in `gradle/libs.versions.toml`. Add new dependencies there first (`[versions]` + `[libraries]`/`[plugins]`), then reference via `libs.<alias>` in `app/build.gradle.kts`.

Compose dependencies must be added before any Compose UI work begins (BOM, `ui`, `ui-tooling`, `material3`, `activity-compose`).

## Development Workflow

- Implement one feature at a time.
- Before coding: state which files will be created or modified and wait for approval on any major architecture change.
- After coding: run `./gradlew assembleDebug` to verify the build.
- Keep the WaveCode encoding format documented in code comments or a markdown file.
- Small, focused commits — do not rewrite unrelated files.
