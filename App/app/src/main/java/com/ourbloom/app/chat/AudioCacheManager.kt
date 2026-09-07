package com.ourbloom.app.chat

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Manages local caching and streaming-free playback of voice notes.
 * Downloading audio to local disk first eliminates MediaPlayer HTTPS drops,
 * Cloudinary content-type issues, latency spikes, and enables instant seek & speed cycling.
 */
object AudioCacheManager {
    private const val TAG = "AudioCacheManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return if (trimmed.startsWith("/")) {
            "https://our-bloom.onrender.com$trimmed"
        } else {
            trimmed
        }
    }

    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "audio_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            input.hashCode().toUInt().toString(16)
        }
    }

    fun getCachedFile(context: Context, rawUrl: String): File {
        val normalized = normalizeUrl(rawUrl)
        val hash = md5(normalized)
        return File(getCacheDir(context), "voice_${hash}.m4a")
    }

    fun isCached(context: Context, rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        val file = getCachedFile(context, rawUrl)
        return file.exists() && file.length() > 500
    }

    /**
     * Cache a local file (e.g. freshly recorded) under its remote URL hash.
     */
    fun saveToCache(context: Context, rawUrl: String, sourceFile: File): File? {
        return try {
            if (!sourceFile.exists() || sourceFile.length() < 500) return null
            val target = getCachedFile(context, rawUrl)
            sourceFile.copyTo(target, overwrite = true)
            target
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save local voice note to cache: ${e.message}")
            null
        }
    }

    /**
     * Fetch from local cache or download over network using resilient OkHttp.
     */
    suspend fun getOrDownloadAudio(context: Context, rawUrl: String): File? = withContext(Dispatchers.IO) {
        if (rawUrl.isBlank()) return@withContext null
        val normalizedUrl = normalizeUrl(rawUrl)

        // Check if rawUrl is already a local file path
        val asDirectFile = File(rawUrl)
        if (asDirectFile.exists() && asDirectFile.length() > 500) {
            return@withContext asDirectFile
        }

        val cachedFile = getCachedFile(context, normalizedUrl)
        if (cachedFile.exists() && cachedFile.length() > 500) {
            return@withContext cachedFile
        }

        try {
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "OurBloom-Android")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP error downloading voice note: code ${response.code} for $normalizedUrl")
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            val tempFile = File.createTempFile("voice_down_", ".tmp", getCacheDir(context))
            tempFile.outputStream().use { output ->
                body.byteStream().copyTo(output)
            }

            if (tempFile.length() > 500) {
                if (cachedFile.exists()) {
                    cachedFile.delete()
                }
                if (tempFile.renameTo(cachedFile)) {
                    return@withContext cachedFile
                } else {
                    tempFile.copyTo(cachedFile, overwrite = true)
                    tempFile.delete()
                    return@withContext cachedFile
                }
            } else {
                tempFile.delete()
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading audio from $normalizedUrl", e)
            return@withContext null
        }
    }

    /**
     * Extract audio duration in milliseconds from local cached audio file using MediaMetadataRetriever.
     */
    fun getAudioDurationMs(context: Context, rawUrlOrPath: String): Long {
        if (rawUrlOrPath.isBlank()) return 0L
        val file = if (File(rawUrlOrPath).exists()) {
            File(rawUrlOrPath)
        } else {
            getCachedFile(context, rawUrlOrPath)
        }
        if (!file.exists() || file.length() < 500) return 0L
        var retriever: android.media.MediaMetadataRetriever? = null
        return try {
            retriever = android.media.MediaMetadataRetriever()
            java.io.FileInputStream(file).use { fis ->
                retriever.setDataSource(fis.fd)
            }
            val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            time?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve audio duration: ${e.message}")
            0L
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
    }
}
