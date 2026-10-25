package com.ourbloom.app.wallpaper

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

enum class WallpaperTarget {
    LOCK_SCREEN,
    HOME_SCREEN,
    BOTH
}

object WallpaperHelper {

    private const val TAG = "WallpaperHelper"

    fun applyWallpaper(context: Context, bitmap: Bitmap, target: WallpaperTarget): Boolean {
        if (bitmap.isRecycled) return false
        return try {
            val wallpaperManager = WallpaperManager.getInstance(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                when (target) {
                    WallpaperTarget.LOCK_SCREEN -> {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                        true
                    }
                    WallpaperTarget.HOME_SCREEN -> {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                        true
                    }
                    WallpaperTarget.BOTH -> {
                        var success = false
                        try {
                            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                            success = true
                        } catch (e: Exception) {
                            Log.w(TAG, "Combined FLAG_SYSTEM or FLAG_LOCK failed, trying separate targets: ${e.message}")
                            try {
                                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                                success = true
                            } catch (e1: Exception) {
                                Log.w(TAG, "FLAG_SYSTEM fallback failed: ${e1.message}")
                            }
                            try {
                                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                                success = true
                            } catch (e2: Exception) {
                                Log.w(TAG, "FLAG_LOCK fallback failed: ${e2.message}")
                            }
                            if (!success) {
                                wallpaperManager.setBitmap(bitmap)
                                success = true
                            }
                        }
                        success
                    }
                }
            } else {
                wallpaperManager.setBitmap(bitmap)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed applying wallpaper: ${e.message}", e)
            false
        }
    }

    fun saveToGallery(context: Context, bitmap: Bitmap, title: String = "OurBloom_Couple_Wallpaper"): Uri? {
        val filename = "${title}_${System.currentTimeMillis()}.jpg"
        var fos: OutputStream? = null
        var imageUri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "OurBloom")
                }
                val resolver = context.contentResolver
                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + File.separator + "OurBloom"
                val file = File(imagesDir)
                if (!file.exists()) {
                    file.mkdirs()
                }
                val image = File(imagesDir, filename)
                fos = FileOutputStream(image)
                imageUri = Uri.fromFile(image)
            }

            if (fos != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 98, fos)
                fos.flush()
            }
            return imageUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving wallpaper to gallery: ${e.message}", e)
            return null
        } finally {
            try {
                fos?.close()
            } catch (_: Exception) {}
        }
    }
}
