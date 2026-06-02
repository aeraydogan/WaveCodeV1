# Barcode-Like Visual Code Decoder Skill

## Purpose

Use this skill for custom visual codes made of bars, stripes, or repeated visual slots.

Examples:
- custom sound code
- tattoo visual code
- barcode-like visual identifier
- Spotify-code-like pattern

## Core Principle

A decoder should not depend only on detecting every physical bar as a perfect connected segment.

For real photos:
- blur merges bars and gaps
- ink spread changes widths
- lighting changes contrast
- small bars may disappear
- exact segment count may fail

Use validation:
- known layout
- known marker positions
- known slot count
- checksum
- confidence score

## Detection Strategies

### Strategy A — Segment-based detection

Good for:
- clean exported PNG
- high contrast images

Weak for:
- blur
- camera photos
- tattoo ink spread

Steps:
1. threshold image
2. horizontal projection
3. find dark segments
4. require expected segment count
5. derive core region

### Strategy B — Slot-based fallback

Good for:
- guided camera capture
- known crop area
- aligned code inside a guide frame

Steps:
1. estimate full code x bounds
2. divide into expected visual slots
3. sample each slot center
4. measure bar height
5. quantize to buckets
6. parse bits
7. validate checksum

## When to Use Slot-Based Fallback

Use slot-based fallback if:
- ROI is guided or manually aligned
- camera crop is roughly correct
- segment-based detector returns 20-39 segments
- bars are visible but not individually separable
- checksum can validate the final read

## For WaveCode-Like Layout

Total visual bars:
- 3 start marker bars
- 34 core bars
- 3 end marker bars
- total = 40 bars

Decoder may:
1. estimate full visual x range
2. divide into 40 bar positions
3. use marker slots to validate orientation
4. decode only the 34 core bars
5. validate checksum

## Debug Logs Required

For slot-based fallback log:
- estimated full xStart/xEnd
- slotWidth
- coreStartX/coreEndX
- marker height ratios
- core height ratios
- bucket string
- checksum result

## Do Not

Do not discard a candidate only because segment count is not exactly expected.

If image is guided and checksum exists, slot-based fallback is acceptable.
