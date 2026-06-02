# WaveCode Decoder Expert Skill

## Project

Android Kotlin + Jetpack Compose app for generating and decoding WaveCode, a tattoo-friendly visual sound identifier.

## Final Goal

Decode a tattoo-like visual code from a phone camera photo.

The visual encodes a publicCode, not audio.

## Existing Decoder Facts

Clean exported PNG decode works.

Camera/photo decode is unstable because:
- blur merges bars/gaps
- crop may include extra background
- thresholding changes segment counts
- segment-based detection often fails
- yBand detection may fallback to full height
- camera crop may be too wide
- BrightCard ROI may include more than code strip

## Layout

Expected visual layout:
- 3 start marker bars
- 34 core bars
- 3 end marker bars
- total visual bars = 40

Core decode:
- 34 bars
- 2 bits per bar
- 68 bits total
- checksum validates result

## Debug Meaning

rawSegs = 1:
- threshold too low or background included

rawSegs = 0:
- threshold too high

rawSegs = 20-39:
- some bars merged/missed
- consider slot-based fallback

rawSegs = 40:
- proceed with sampler/parser

checksum fail:
- region probably close but quantization/sampling wrong

## Preferred Decoder Strategy

1. Try clean segment-based detection.
2. If it fails but ROI is guided/aligned, use slot-based fallback.
3. In slot-based fallback:
   - estimate full WaveCode x bounds
   - divide into 40 visual slots
   - validate start/end marker positions
   - decode 34 core slots
   - validate checksum

## Camera Scan Direction

Use guided camera crop:
- user aligns code horizontally in a 3:1 frame
- capture image
- crop guide area
- pass cropped bitmap to decoder

Since crop is guided, decoder can rely more on expected slot layout than arbitrary full-image detection.

## Do Not

Do not change:
- encoder
- binary format
- renderer/exporter
- publicCode format

Unless the user explicitly asks.

## Minimal Debug Before Fix

Before changing algorithm, log:
- cropped preview size
- ROI size/aspect
- yBand
- projection stats
- rawSegs
- threshold candidates
- slot height ratios if fallback is used
- bucket string
- checksum result
