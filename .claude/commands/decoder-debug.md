# Image Processing Decoder Debugger Skill

## Purpose

Use this skill when debugging a visual-code decoder that reads bars, stripes, waveforms, or barcode-like images from camera/gallery bitmaps.

The goal is to avoid random trial-and-error and instead identify the exact failure stage.

## Required Debug Pipeline

For every decode failure, inspect and log:

1. Input bitmap
- width
- height
- orientation
- source: clean PNG / gallery / camera crop / ROI crop

2. Preprocessing
- grayscale/luminance conversion
- contrast stretch enabled/disabled
- binary threshold value
- threshold method: fixed / Otsu / adaptive

3. ROI
- ROI type
- ROI bounds
- ROI size
- ROI aspect ratio
- whether ROI contains only code or extra UI/background

4. yBand / vertical band
- selected yMin/yMax
- band height ratio
- fallback reason if full height used

5. Projection stats
For horizontal projection:
- min
- max
- p10
- p50
- p90
- chosen threshold
- raw segment count

6. Segment merging
For each gap setting:
- gap value
- segment count after merge
- first/last segment bounds
- average segment width
- min/max segment width

7. Sampling
- sampled slot count
- height ratios
- bucket values
- per-bar confidence

8. Bit parsing
- raw bits length
- start marker valid
- version valid
- end marker valid
- checksum valid
- publicCode if success

## Diagnosis Rules

If rawSegs = 1:
- projection threshold is too low
- full-height projection includes background
- gaps are not dropping below threshold
- blur or background is merging everything into one segment

If rawSegs = 0:
- projection threshold is too high
- threshold may equal max projection
- binary threshold may classify bars as background
- yBand may not include bars

If rawSegs is between 20 and 39:
- some bars are too weak or merged
- image may be blurred
- threshold too high
- bar widths too narrow
- segment-based detection may be too fragile
- consider slot-based fallback

If rawSegs is above 45:
- noise/text/background creates false segments
- ROI too large
- yBand too tall
- threshold too low

If yBand is nearly full height:
- yBand detector is not isolating code band
- ROI may be too tall
- row projection strategy may be wrong

If ROI aspect ratio differs significantly from expected:
- crop may include extra UI/padding
- ROI detector is finding card, not code strip
- need secondary code-strip detection inside ROI

## Recommended Fix Order

1. Show/save cropped input preview.
2. Show/save ROI crop preview.
3. Fix crop/ROI first.
4. Fix yBand selection.
5. Fix projection threshold.
6. Add threshold retry.
7. Add slot-based fallback.
8. Only then consider larger architecture changes.

## Important

Do not change the encoder or binary format during decoder debugging unless the format is proven undecodable.

Always preserve clean PNG decode behavior.
