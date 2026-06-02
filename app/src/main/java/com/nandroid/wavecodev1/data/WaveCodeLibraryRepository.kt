package com.nandroid.wavecodev1.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

private const val TAG = "WaveCodeLibraryRepo"
private const val LIBRARY_FILE = "wavecode_library.json"

/**
 * Local-only persistence for WaveCode → audio mappings.
 *
 * Backed by a single JSON file in app-private internal storage (filesDir). No database, no
 * backend — this is the first storage layer for the local lifecycle. Access is synchronized
 * and the in-memory list is the source of truth once loaded; callers should run mutations off
 * the main thread.
 *
 * Replaceable later by Room / a backend without changing call sites: keep the public surface
 * (add / findByCode / all / remove) stable.
 */
class WaveCodeLibraryRepository private constructor(private val appContext: Context) {

    private val file: File get() = File(appContext.filesDir, LIBRARY_FILE)
    private val lock = Any()

    @Volatile
    private var cache: MutableList<WaveCodeEntry>? = null

    /** Returns all entries, newest first. */
    fun all(): List<WaveCodeEntry> = synchronized(lock) {
        load().sortedByDescending { it.createdAt }
    }

    /** Resolves a decoded publicCode back to its saved entry, or null if not found. */
    fun findByCode(publicCode: String): WaveCodeEntry? = synchronized(lock) {
        load().firstOrNull { it.publicCode == publicCode }
    }

    /** Adds (or replaces, by publicCode) an entry and persists. */
    fun add(entry: WaveCodeEntry) = synchronized(lock) {
        val list = load()
        list.removeAll { it.publicCode == entry.publicCode }
        list.add(entry)
        persist(list)
    }

    /** Removes the entry for [publicCode] if present and persists. */
    fun remove(publicCode: String) = synchronized(lock) {
        val list = load()
        if (list.removeAll { it.publicCode == publicCode }) persist(list)
    }

    // ── Internal ────────────────────────────────────────────────────────────────────────────────

    private fun load(): MutableList<WaveCodeEntry> {
        cache?.let { return it }
        val list = mutableListOf<WaveCodeEntry>()
        if (file.exists()) {
            try {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    list.add(WaveCodeEntry.fromJson(arr.getJSONObject(i)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "load: corrupt library file, starting empty", e)
            }
        }
        cache = list
        return list
    }

    private fun persist(list: MutableList<WaveCodeEntry>) {
        cache = list
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            file.writeText(arr.toString())
            Log.d(TAG, "persist: ${list.size} entries written")
        } catch (e: Exception) {
            Log.e(TAG, "persist failed", e)
        }
    }

    companion object {
        @Volatile
        private var instance: WaveCodeLibraryRepository? = null

        fun get(context: Context): WaveCodeLibraryRepository =
            instance ?: synchronized(this) {
                instance ?: WaveCodeLibraryRepository(context.applicationContext).also { instance = it }
            }
    }
}