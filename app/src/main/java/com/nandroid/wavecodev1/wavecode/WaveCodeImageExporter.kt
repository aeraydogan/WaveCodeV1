package com.nandroid.wavecodev1.wavecode

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "WaveCodeImageExporter"

/**
 * Renders the WaveCode to a high-resolution Bitmap using Android Canvas (no Compose).
 *
 * Visual accuracy contract:
 * - Uses WaveCodeSpec.computeLevels() — same function as WaveCodeRenderer — so preview
 *   and exported PNG always produce identical bar heights for the same inputs.
 * - Geometry constants (quiet zone, bar fill ratio, corner radius, etc.) mirror
 *   WaveCodeRenderer exactly. If you update geometry in either file, update both.
 * - dp → px conversion uses exportSettings.dpi as the screen density:
 *     pxValue = dpValue × (exportDpi / 160f)
 *   This produces consistent physical sizes at the target print resolution.
 *
 * DECODER NOTE:
 * - Decode only the 34 core bars (between the two edge gaps).
 * - Decorative start/end markers (3 per side, levels 0/0/1 and 1/0/0) are NOT encoded data.
 * - Use WaveCodeSpec.decodeThresholds(variant) for height-bucket mapping.
 * - Manual visual overrides are decode-safe: both sub-levels in a bucket decode identically.
 */
object WaveCodeImageExporter {

    fun exportToBitmap(
        data: WaveCodeData,
        variant: WaveCodeVisualVariant,
        overrides: Map<Int, Int>,
        settings: WaveCodeExportSettings
    ): Bitmap {
        val w      = settings.exportPixelWidth
        val h      = settings.exportPixelHeight
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG)

        // dp → px at export DPI (mirrors Android's dp definition: px = dp × density / 160)
        val dpScale = settings.dpi / 160f

        val canvasW = w.toFloat()
        val canvasH = h.toFloat()

        // ── Geometry — keep in sync with WaveCodeRenderer ────────────────────────────────────────
        val quietZonePx  = 12f * dpScale
        val markerCount  = 3
        val edgeGapPx    = 12f * dpScale
        val vertPadPx    = 12f * dpScale
        val usableHeight = canvasH - 2f * vertPadPx
        val centerY      = canvasH / 2f

        val groups           = data.bits.chunked(WaveCodeSpec.BITS_PER_BAR)
        // Proportional marker slots — marker slot scales with core slot so bars never overflow
        // at high-resolution export. 6 total marker slots (3 start + 3 end).
        val usableForBars    = canvasW - 2f * quietZonePx - 2f * edgeGapPx
        val coreSlotWidth    = usableForBars / (groups.size + 6f * WaveCodeSpec.MARKER_SLOT_FRACTION)
        val markerSlotWidth  = coreSlotWidth * WaveCodeSpec.MARKER_SLOT_FRACTION
        val coreBarWidth     = (coreSlotWidth * WaveCodeSpec.CORE_BAR_FILL).coerceAtLeast(2f * dpScale)
        val coreCornerRadius = coreBarWidth / 2f
        val markerRegionWidth = markerCount * markerSlotWidth
        val coreStartX       = quietZonePx + markerRegionWidth + edgeGapPx

        val markerBarWidth     = (coreBarWidth * WaveCodeSpec.MARKER_BAR_FILL).coerceAtLeast(1.5f * dpScale)
        val markerCornerRadius = markerBarWidth / 2f

        val lvl0H = usableHeight * WaveCodeSpec.heightForLevel(0, variant)
        val lvl1H = usableHeight * WaveCodeSpec.heightForLevel(1, variant)
        // ─────────────────────────────────────────────────────────────────────────────────────────

        // White background
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, canvasW, canvasH, paint)

        paint.color = Color.BLACK

        // ── Start decorative markers: [level 0, level 0, level 1] outer → inner ─────────────────
        // NOT part of the 34 core bars. Decoder must ignore these.
        val startHeights = floatArrayOf(lvl0H, lvl0H, lvl1H)
        repeat(markerCount) { i ->
            val slotLeft = quietZonePx + i * markerSlotWidth
            val barLeft  = slotLeft + (markerSlotWidth - markerBarWidth) / 2f
            val mh       = startHeights[i]
            canvas.drawRoundRect(
                RectF(barLeft, centerY - mh / 2f, barLeft + markerBarWidth, centerY + mh / 2f),
                markerCornerRadius, markerCornerRadius, paint
            )
        }

        // ── End decorative markers: [level 1, level 0, level 0] inner → outer ───────────────────
        // NOT part of the 34 core bars. Decoder must ignore these.
        val endMarkerStartX = coreStartX + groups.size * coreSlotWidth + edgeGapPx
        val endHeights      = floatArrayOf(lvl1H, lvl0H, lvl0H)
        repeat(markerCount) { i ->
            val slotLeft = endMarkerStartX + i * markerSlotWidth
            val barLeft  = slotLeft + (markerSlotWidth - markerBarWidth) / 2f
            val mh       = endHeights[i]
            canvas.drawRoundRect(
                RectF(barLeft, centerY - mh / 2f, barLeft + markerBarWidth, centerY + mh / 2f),
                markerCornerRadius, markerCornerRadius, paint
            )
        }

        // ── Core bars — 34 machine-readable floating capsules ────────────────────────────────────
        // Uses the same computeLevels() as WaveCodeRenderer; preview and PNG are always in sync.
        val levels = WaveCodeSpec.computeLevels(data, variant, overrides)
        levels.forEachIndexed { index, level ->
            val barHeight = usableHeight * WaveCodeSpec.heightForLevel(level, variant)
            val slotLeft  = coreStartX + index * coreSlotWidth
            val barLeft   = slotLeft + (coreSlotWidth - coreBarWidth) / 2f
            canvas.drawRoundRect(
                RectF(barLeft, centerY - barHeight / 2f, barLeft + coreBarWidth, centerY + barHeight / 2f),
                coreCornerRadius, coreCornerRadius, paint
            )
        }

        return bitmap
    }

    /**
     * Compresses [bitmap] to PNG and saves it so it appears in the phone Gallery.
     *
     * API 29+ (Android 10+): MediaStore.Images.Media with IS_PENDING lifecycle.
     *   - IS_PENDING = 1 hides the row from gallery apps during the write.
     *   - IS_PENDING = 0 after a successful write makes the row visible and triggers indexing.
     *   - No permission required.
     *
     * API 24–28 (Android 7–9): Public Pictures directory + MediaScannerConnection.
     *   - Requires WRITE_EXTERNAL_STORAGE (declared in manifest, maxSdkVersion="28").
     *   - If permission is not granted, returns Failure with a clear message.
     *   - MediaScannerConnection.scanFile() triggers Gallery indexing after write.
     */
    fun savePng(context: Context, bitmap: Bitmap, publicCode: String): SaveResult {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename  = "WaveCode_${publicCode}_$timestamp.png"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "savePng: using MediaStore branch (API ${Build.VERSION.SDK_INT})")
            saveToMediaStore(context, bitmap, filename)
        } else {
            Log.d(TAG, "savePng: using legacy public Pictures branch (API ${Build.VERSION.SDK_INT})")
            saveToLegacyPublicPictures(context, bitmap, filename)
        }
    }

    // ── API 29+ ──────────────────────────────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(context: Context, bitmap: Bitmap, filename: String): SaveResult {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WaveCode")
            // IS_PENDING = 1: hide from gallery during write; prevents incomplete-file indexing
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri      = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return SaveResult.Failure("Could not create MediaStore entry")

        Log.d(TAG, "saveToMediaStore: row inserted uri=$uri — writing bitmap with IS_PENDING=1")

        return try {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            // Clear IS_PENDING so gallery apps index and display the fully written file
            val updateValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            val updatedRows = resolver.update(uri, updateValues, null, null)
            Log.d(TAG, "saveToMediaStore: IS_PENDING cleared (rows=$updatedRows), uri=$uri")
            SaveResult.Success(filename, uri)
        } catch (e: Exception) {
            Log.e(TAG, "saveToMediaStore: write failed, deleting MediaStore row", e)
            resolver.delete(uri, null, null)
            SaveResult.Failure("Write failed: ${e.message}")
        }
    }

    // ── API 24–28 ─────────────────────────────────────────────────────────────────────────────────

    private fun saveToLegacyPublicPictures(
        context: Context,
        bitmap: Bitmap,
        filename: String
    ): SaveResult {
        // WRITE_EXTERNAL_STORAGE is required for public external storage on API < 29.
        // It is declared in AndroidManifest.xml with maxSdkVersion="28" and must be
        // granted at runtime; if not granted, return a clear failure.
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "saveToLegacyPublicPictures: WRITE_EXTERNAL_STORAGE not granted")
            return SaveResult.Failure("Storage permission required on this Android version")
        }

        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dir     = File(baseDir, "WaveCode").apply { mkdirs() }
        val file    = File(dir, filename)

        return try {
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            // MediaScannerConnection is required on API < 29 to add the file to the media
            // database. Without it the file exists on disk but never appears in Gallery apps.
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("image/png"),
                null
            )
            val uri = Uri.fromFile(file)
            Log.d(TAG, "saveToLegacyPublicPictures: saved ${file.absolutePath}, scan triggered, uri=$uri")
            SaveResult.Success(filename, uri)
        } catch (e: Exception) {
            Log.e(TAG, "saveToLegacyPublicPictures: write failed", e)
            SaveResult.Failure("Write failed: ${e.message}")
        }
    }

    sealed class SaveResult {
        data class Success(val filename: String, val uri: Uri) : SaveResult()
        data class Failure(val message: String) : SaveResult()
    }
}
