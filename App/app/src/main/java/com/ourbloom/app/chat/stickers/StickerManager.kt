package com.ourbloom.app.chat.stickers

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

object StickerManager {

    private const val TAG = "StickerManager"
    private const val PREFS_NAME = "ourbloom_stickers_prefs"
    private const val KEY_FAVORITES = "sticker_favorites"
    private const val KEY_RECENTS = "sticker_recents"
    private const val KEY_SAF_TREE_URI = "saf_whatsapp_tree_uri"

    data class Sticker(
        val file: File,
        val isFavorite: Boolean = false,
        val isRecent: Boolean = false
    ) {
        val path: String get() = file.absolutePath
        val name: String get() = file.name
    }

    fun getWhatsAppStickersDir(context: Context): File {
        val dir = File(context.filesDir, "stickers/whatsapp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCustomStickersDir(context: Context): File {
        val dir = File(context.filesDir, "stickers/custom")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Attempts to auto-scan standard WhatsApp directories on device storage.
     * Works on Android versions / devices where direct read access is available.
     */
    fun scanDeviceWhatsAppStickers(): List<File> {
        val foundFiles = mutableListOf<File>()
        val possibleDirs = listOf(
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Stickers"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Stickers"),
            File(Environment.getExternalStorageDirectory(), "WhatsApp Business/Media/WhatsApp Business Stickers")
        )

        for (dir in possibleDirs) {
            try {
                if (dir.exists() && dir.isDirectory && dir.canRead()) {
                    val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".webp", ignoreCase = true) }
                    if (!files.isNullOrEmpty()) {
                        foundFiles.addAll(files)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot scan dir ${dir.absolutePath}: ${e.message}")
            }
        }
        return foundFiles.sortedByDescending { it.lastModified() }
    }

    /**
     * Imports files directly from a scanned list of Files into internal storage.
     */
    suspend fun importFiles(context: Context, files: List<File>): Int = withContext(Dispatchers.IO) {
        val destDir = getWhatsAppStickersDir(context)
        var importedCount = 0
        for (src in files) {
            try {
                val dest = File(destDir, src.name)
                if (!dest.exists() || dest.length() != src.length()) {
                    src.copyTo(dest, overwrite = true)
                    importedCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error importing file ${src.name}", e)
            }
        }
        importedCount
    }

    /**
     * Imports stickers from a list of Uris (e.g. from ACTION_OPEN_DOCUMENT or ACTION_GET_CONTENT).
     */
    suspend fun importFromUris(context: Context, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val destDir = getWhatsAppStickersDir(context)
        var importedCount = 0
        val timestamp = System.currentTimeMillis()

        for ((index, uri) in uris.withIndex()) {
            try {
                val filename = getFileNameFromUri(context, uri) ?: "wa_stk_${timestamp}_$index.webp"
                val destFile = File(destDir, if (filename.endsWith(".webp", ignoreCase = true)) filename else "$filename.webp")
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    importedCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed importing uri: $uri", e)
            }
        }
        importedCount
    }

    /**
     * Imports all .webp stickers from an SAF Document Tree (Folder picker).
     */
    suspend fun importFromTreeUri(context: Context, treeUri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            // Save tree uri for recurring refresh
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SAF_TREE_URI, treeUri.toString())
                .apply()

            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
            val destDir = getWhatsAppStickersDir(context)
            var count = 0

            val files = rootDoc.listFiles()
            for (docFile in files) {
                if (docFile.isFile && (docFile.name?.endsWith(".webp", ignoreCase = true) == true || docFile.type == "image/webp")) {
                    val name = docFile.name ?: "stk_${System.currentTimeMillis()}_$count.webp"
                    val dest = File(destDir, name)
                    if (!dest.exists()) {
                        context.contentResolver.openInputStream(docFile.uri)?.use { input ->
                            FileOutputStream(dest).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (dest.exists() && dest.length() > 0) count++
                    }
                }
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Error importing from tree uri", e)
            0
        }
    }

    /**
     * Imports a single sticker from a share Intent (ACTION_SEND).
     */
    suspend fun importFromShareUri(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val destDir = getWhatsAppStickersDir(context)
            val name = "shared_${System.currentTimeMillis()}.webp"
            val dest = File(destDir, name)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            if (dest.exists() && dest.length() > 0) {
                addRecent(context, dest.absolutePath)
                dest
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing shared sticker", e)
            null
        }
    }

    fun extractBundledStickers(context: Context) {
        try {
            val destDir = getWhatsAppStickersDir(context)
            val assetList = context.assets.list("stickers") ?: return
            for (assetName in assetList) {
                if (assetName.endsWith(".webp", ignoreCase = true)) {
                    val destFile = File(destDir, assetName)
                    if (!destFile.exists()) {
                        context.assets.open("stickers/$assetName").use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting bundled stickers: ${e.message}")
        }
    }

    /**
     * Retrieves all imported WhatsApp stickers.
     */
    fun getAllWhatsAppStickers(context: Context): List<Sticker> {
        val dir = getWhatsAppStickersDir(context)
        var files = dir.listFiles { f -> f.isFile && f.name.endsWith(".webp", ignoreCase = true) } ?: emptyArray()
        if (files.isEmpty()) {
            extractBundledStickers(context)
            files = dir.listFiles { f -> f.isFile && f.name.endsWith(".webp", ignoreCase = true) } ?: emptyArray()
        }
        val favorites = getFavoritesSet(context)
        return files.sortedByDescending { it.lastModified() }.map { file ->
            Sticker(file = file, isFavorite = favorites.contains(file.absolutePath))
        }
    }

    /**
     * Favorites management
     */
    fun getFavoritesSet(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun toggleFavorite(context: Context, filePath: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toMutableSet() ?: mutableSetOf()
        val isFav: Boolean
        if (current.contains(filePath)) {
            current.remove(filePath)
            isFav = false
        } else {
            current.add(filePath)
            isFav = true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return isFav
    }

    fun isFavorite(context: Context, filePath: String): Boolean {
        return getFavoritesSet(context).contains(filePath)
    }

    fun getFavoriteStickers(context: Context): List<Sticker> {
        val favPaths = getFavoritesSet(context)
        val list = mutableListOf<Sticker>()
        for (path in favPaths) {
            val file = File(path)
            if (file.exists()) {
                list.add(Sticker(file = file, isFavorite = true))
            }
        }
        return list.sortedByDescending { it.file.lastModified() }
    }

    /**
     * Recents management
     */
    fun addRecent(context: Context, filePath: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val rawJson = prefs.getString(KEY_RECENTS, "[]") ?: "[]"
            val array = JSONArray(rawJson)
            val updated = mutableListOf<String>()
            updated.add(filePath)

            for (i in 0 until array.length()) {
                val p = array.optString(i)
                if (p.isNotBlank() && p != filePath && updated.size < 40) {
                    updated.add(p)
                }
            }

            val newArray = JSONArray()
            updated.forEach { newArray.put(it) }
            prefs.edit().putString(KEY_RECENTS, newArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding recent sticker", e)
        }
    }

    fun getRecentStickers(context: Context): List<Sticker> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_RECENTS, "[]") ?: "[]"
        val favorites = getFavoritesSet(context)
        val result = mutableListOf<Sticker>()

        try {
            val array = JSONArray(rawJson)
            for (i in 0 until array.length()) {
                val path = array.optString(i)
                if (path.isNotBlank()) {
                    val file = File(path)
                    if (file.exists()) {
                        result.add(Sticker(file = file, isFavorite = favorites.contains(path), isRecent = true))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading recents", e)
        }
        return result
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) name = it.getString(idx)
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { p ->
                val cut = p.lastIndexOf('/')
                if (cut != -1) p.substring(cut + 1) else p
            }
        }
        return name
    }
}
