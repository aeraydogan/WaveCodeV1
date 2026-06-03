# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**VoiceWave / WaveCode** — An Android app that records audio, generates a tattoo-friendly visual code (called a **WaveCode**), and later scans that code to play the original audio.

The full product context, encode/decode format, theme rules, and UX direction live in the **WaveCode / Ses Görsel Dövme Project Context** section below. Read that section before touching WaveCode-related code.

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

# WaveCode / Ses Görsel Dövme Project Context

## Product Goal

WaveCode is an Android app/product that turns a recorded or selected audio message into a tattoo-friendly, machine-readable visual code.

The WaveCode visual can be tattooed on skin, photographed/scanned later, decoded into a short Ses Kodu/publicCode, resolved from backend, and played as the original audio.

Flow:
- record/select audio
- → upload audio to backend
- → backend generates 8-character publicCode
- → Android generates WaveCode visual
- → user saves/shares/tattoos it
- → later gallery/camera scan decodes publicCode
- → Android resolves audio from backend
- → audio plays.

A waveform image alone cannot reconstruct audio. The visual never stores audio data itself — it encodes only the short `publicCode` (e.g. `A7K29XQ4`), which is resolved to an audio file via backend lookup.

WaveCode is **not** a QR code, **not** a standard barcode, and **not** decorative art only.
It is a machine-readable tattoo system.

## Backend Rule

Backend is the source of truth for `publicCode -> audio` mapping.

Android must **not** trust backend `audioUrl` host if it points to localhost.
Client should build audio URL as:

```
BASE_URL + "/api/voice-codes/{publicCode}/audio"
```

## User-facing Terminology

Use Turkish UI text.

Use:
- Ses Kodu
- Dövme Oluştur
- Tara & Dinle
- Dövmeyi Teninde Gör

Avoid (in user-facing text):
- publicCode
- backend
- metadata
- resolve
- upload

## WaveCode Encode/Decode Format

WaveCode encodes the backend-generated 8-character `publicCode`, **not** raw audio.

Current visual structure:
- 3 decorative start marker bars
- 34 core data bars
- 3 decorative end marker bars
- total visual bars: **40**

Only the 34 core bars encode data.
The 3+3 decorative marker bars are **not** payload.
They help with visual/finder/orientation behavior.

Current marker pattern:
- start marker: level 0, level 0, level 1
- end marker: level 1, level 0, level 0

Core data:
- 34 core bars
- each core bar represents **2 bits**
- total core bit length: **68 bits**

Binary payload structure:
- START_MARKER
- VERSION_BITS
- publicCode payload bits
- CHECKSUM
- END_MARKER

publicCode:
- length: 8 characters
- allowed alphabet: `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`

Visual levels:
- WaveCode may use multiple bar height levels for tattoo aesthetics.
- Decode maps visual levels into 4 logical 2-bit buckets.

Logical bucket mapping:
- bucket 0 → `00`
- bucket 1 → `01`
- bucket 2 → `10`
- bucket 3 → `11`

If using 8 visual levels:
- level 0 or 1 → bucket 0 / `00`
- level 2 or 3 → bucket 1 / `01`
- level 4 or 5 → bucket 2 / `10`
- level 6 or 7 → bucket 3 / `11`

Encode must preserve:
- bar count
- bar order
- bar x positions
- bar spacing
- bar heights / encoded levels
- visual level to bucket mapping
- start/end marker positions
- quiet zone / safe margin
- high contrast
- core geometry expected by decoder

Decode must validate:
- start marker / format marker
- version bits
- payload length
- 8-character publicCode
- checksum
- end marker

## Sacred Core Rule

The readable WaveCode core is **sacred**.

Do **not** change:
- 34 core bars
- 3 start marker bars
- 3 end marker bars
- spacing
- x positions
- quiet zone
- binary format
- bucket mapping

unless explicitly requested.

## WaveCode Visual Style

The WaveCode visual looks like a stylized waveform / Spotify-code-like vertical bar pattern — black and white, high contrast, tattoo-friendly. When improving the visual, always work within a named design direction. Do not mix directions unless explicitly approved.

**Direction 1 — Spotify-like clean style** *(current approved direction)*
- Clean, minimal, premium floating capsule bar style
- Inspired by Spotify-code-like vertical rhythm — not copying Spotify's exact format
- Black rounded capsule bars on white (or skin-tone) background
- Bars are **vertically centered** on the canvas — each bar floats in the middle, not anchored to any edge
- Bar heights vary by encoded value; shorter bars appear as small centered capsules, taller bars fill more of the vertical space
- **No baseline stroke** — bars float freely with no connecting line beneath them
- Spacing is balanced and readable
- No decorative outer shell
- No bottom-anchored / baseline-anchored layout
- No center-symmetric waveform (bars are not mirrored top/bottom)
- No chaotic artistic shapes
- Priority: readability, tattoo-friendliness, clean product look

This visual style sits on top of, and must never violate, the **Sacred Core Rule** above.

## Theme Rules

Themes must be **non-destructive overlays**.

Allowed:
- outer frame
- ornaments outside quiet zone
- typography outside decode zone
- skin-tone background
- tattoo preview composition
- premium visual layout around the core

Not allowed inside decode zone:
- dots
- stars
- leaves
- runes
- brush strokes
- signatures
- mandala lines
- decorative noise
- anything between bars

Themes must not alter, overlap, merge, distort, erase, or add noise to the readable core.

## Tattoo Readability Rules

Always consider:
- tattoo aging
- ink spread
- skin texture
- skin tone
- glare
- shadows
- camera blur
- photo noise
- crop mismatch

Prefer:
- bold enough dark bars
- clean gaps
- high contrast
- quiet zone
- simple core geometry

Avoid:
- thin details near bars
- low contrast
- decorative marks inside quiet zone
- rough texture over core bars

## Current UX Direction

Home:
- Dövme Oluştur
- Tara & Dinle

Dövme Oluştur:
- title input
- record audio
- show WaveCode preview
- white background tab
- skin-tone background tab
- show Ses Kodu
- copy code with green check feedback
- save selected visual
- save/upload to server
- Dövmeyi Teninde Gör

Dövmeyi Teninde Gör:
- gallery/camera photo selection
- place WaveCode interactively on photo
- move/scale/rotate
- save/share final composed preview

Tara & Dinle:
- gallery scan
- camera scan with guide frame
- decode publicCode
- resolve from backend
- play audio
- manual code entry only as fallback if needed

Camera:
- prefer guided capture with centered rectangular guide frame
- reuse existing CameraX guide-frame screen
- show captured image, decoded Ses Kodu, metadata, Oynat/Tekrar Oynat controls
- do not rewrite camera algorithm unless explicitly requested

## Claude Code Working Rules

Prefer small phased changes:
- one screen
- one feature
- one bug fix

Before changing algorithms:
- inspect real code
- inspect logs
- identify root cause
- add targeted logs if needed
- implement minimal fix

Always mention what must not change:
- encoder
- decoder core
- binary format
- backend contract
- camera algorithm

Avoid broad rewrites.
Keep token usage efficient.
