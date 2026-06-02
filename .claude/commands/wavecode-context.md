# WaveCode Project Context Skill

## Product Goal

This Android project creates and scans tattoo-friendly visual sound codes called WaveCode.

The final use case is:

1. User records or selects a voice/audio.
2. App generates a tattoo-friendly WaveCode visual.
3. The WaveCode is tattooed on skin.
4. Later, the app scans the tattoo/photo.
5. App decodes a short publicCode from the visual.
6. publicCode maps to the saved audio.

Important:
- The visual does NOT reconstruct audio directly.
- The visual encodes a short publicCode.
- Decoder must eventually work on tattoo photos, not only clean PNGs.

## Current WaveCode Format

WaveCode visual contains:
- 3 decorative start marker bars
- gap
- 34 core bars
- gap
- 3 decorative end marker bars

Core data:
- 34 core bars
- each bar represents 2 bits
- total 68 bits

Binary structure:
- START_MARKER
- VERSION_BITS
- PAYLOAD
- CHECKSUM
- END_MARKER

Decoder must validate:
- start marker
- version
- end marker
- checksum

## Visual Style

WaveCode is inspired by a Spotify-code-like clean bar style, but it is custom.

Bars are:
- vertical
- center aligned
- floating, not baseline-only
- black on light background in export
- eventually black ink on skin in tattoo

## Important Decoder Principle

Do not rely only on exact exported PNG dimensions.

Decoder must eventually handle:
- photo blur
- lighting variation
- skin background
- ink spread
- crop/scale differences
- minor rotation
- perspective distortion

## Current Problem History

Clean exported PNG decode works.

Gallery/camera photo decode has been difficult because:
- arbitrary photo rotation
- dark app background
- crop mismatch
- CameraX rotation
- RegionDetector expecting exactly 40 detected segments
- horizontal projection often sees too few segments
- blur merges bars/gaps
- thresholding and y-band selection are fragile

## Current Preferred Direction

For camera scanning:
- Use in-app CameraX scan screen.
- Show a guide frame.
- User aligns WaveCode horizontally inside frame.
- App captures image.
- App crops guide-frame area.
- Decoder processes cropped bitmap.

This reduces:
- arbitrary rotation
- arbitrary ROI search
- full-photo background noise

## What Claude Should Avoid

Do not:
- change encoder format unless explicitly asked
- change binary format unless absolutely necessary
- add QR/barcode libraries
- add heavy dependencies
- rewrite entire decoder without diagnosis
- assume clean PNG only
- assume white background only
- overfit to one test image
- make token-heavy broad plans when a narrow fix is requested

## Debug Philosophy

When decode fails:
1. Check cropped preview.
2. Check ROI/candidate bitmap size.
3. Check binary threshold.
4. Check yBand.
5. Check projection stats.
6. Check raw segment count.
7. Check sampled bar height ratios.
8. Check bucket string.
9. Check checksum result.

Always prefer adding targeted diagnostic logs over guessing.
