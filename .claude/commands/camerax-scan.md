# Android CameraX Scan Expert Skill

## Purpose

Use this skill when implementing CameraX scan screens in Android Compose.

## Preferred First Version

For first implementation:
- use CameraX PreviewView
- use ImageCapture.takePicture
- show a guide frame overlay
- capture manually with a button
- crop the guide-frame area
- decode on background thread

Do not start with live ImageAnalysis unless explicitly requested.

## Required Camera Debug Logs

On capture:
- ImageProxy width/height
- rotationDegrees
- raw bitmap width/height
- upright bitmap width/height
- crop rect
- cropped bitmap width/height
- guide frame fractions
- margin fraction
- crop aspect ratio

## Rotation

Always apply:
- imageProxy.imageInfo.rotationDegrees

Captured bitmap passed to decoder should match what user saw in preview.

## Crop

For guide-frame scanning:
- crop should roughly match guide overlay
- include small safety margin
- do not let margin clamp crop to full width unless intentional
- show cropped preview in UI

## Preview Mapping

PreviewView may use scaling/cropping such as FILL_CENTER.

For early PoC:
- center crop approximation is acceptable
- cropped preview must be shown for verification

For production:
- implement exact PreviewView-to-image coordinate mapping

## UI Requirements

Scan screen should show:
- camera preview
- guide frame
- instruction text
- capture/scan button
- cropped preview after capture
- decode result
- scan again button

## Future Work

- ImageAnalysis live scan
- torch toggle
- focus/metering on guide area
- perspective correction
- quality guidance
- auto-capture when aligned
