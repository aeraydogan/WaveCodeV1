package com.nandroid.wavecodev1.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

private const val TAG = "AudioRecorder"

/**
 * Thin MediaRecorder wrapper producing an AAC/MPEG-4 (.m4a) file.
 *
 * Requires the RECORD_AUDIO permission to be granted before [start]. Not thread-safe; drive it
 * from a single coroutine/UI flow. Always call [stop] (or [cancel]) to release the recorder.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    /**
     * Peak amplitude (0..32767) captured since the previous call, or 0 when not recording.
     * Used to drive the live recording level meter. Safe to poll from the UI.
     */
    fun maxAmplitude(): Int = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }

    /** Begins recording into [output]. Returns false if setup fails. */
    fun start(output: File): Boolean {
        if (recorder != null) {
            Log.w(TAG, "start: already recording")
            return false
        }
        return try {
            val rec = createRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(output.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            outputFile = output
            Log.d(TAG, "start: recording to ${output.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            releaseQuietly()
            false
        }
    }

    /** Stops and finalizes the recording; returns the written file, or null on failure. */
    fun stop(): File? {
        val rec = recorder ?: return null
        return try {
            rec.stop()
            val file = outputFile
            Log.d(TAG, "stop: finalized ${file?.absolutePath} (${file?.length()} bytes)")
            file
        } catch (e: Exception) {
            // stop() throws if stopped too early / no frames captured — discard partial file.
            Log.e(TAG, "stop failed; discarding partial file", e)
            outputFile?.delete()
            null
        } finally {
            releaseQuietly()
        }
    }

    /** Aborts recording and deletes any partial file. */
    fun cancel() {
        try { recorder?.stop() } catch (_: Exception) {}
        outputFile?.delete()
        releaseQuietly()
    }

    private fun releaseQuietly() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        outputFile = null
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
        else MediaRecorder()
}