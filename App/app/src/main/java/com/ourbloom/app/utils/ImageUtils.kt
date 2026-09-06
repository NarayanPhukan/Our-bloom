package com.ourbloom.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File

object ImageUtils {

    /**
     * Prepares an image from a Uri with Ultra-HD quality (up to 2560px max dimension, 92% JPEG quality),
     * auto-correcting EXIF orientation so photos are never rotated sideways or blurry.
     */
    fun processHighQualityImage(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2560,
        quality: Int = 92
    ): ByteArray? {
        return try {
            // 1. Read bounds first
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) {
                // Fallback to raw bytes if decoding bounds failed
                return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }

            // 2. Determine sample size to avoid OutOfMemory on huge camera images
            var sampleSize = 1
            var maxSide = maxOf(origWidth, origHeight)
            while (maxSide / 2 >= maxDimension) {
                sampleSize *= 2
                maxSide /= 2
            }

            // 3. Decode bitmap with sampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

            // 4. Correct EXIF orientation
            val rotation = getExifRotation(context, uri)
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }

            // 5. If still larger than maxDimension, scale smoothly with bilinear filtering
            val currentMax = maxOf(bitmap.width, bitmap.height)
            if (currentMax > maxDimension) {
                val ratio = maxDimension.toFloat() / currentMax.toFloat()
                val targetW = (bitmap.width * ratio).toInt()
                val targetH = (bitmap.height * ratio).toInt()
                val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            // 6. Compress with high-fidelity JPEG (92%)
            val outStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
            bitmap.recycle()
            outStream.toByteArray()
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error processing high-quality image: ${e.message}", e)
            try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun getExifRotation(context: Context, uri: Uri): Int {
        try {
            val tempFile = File(context.cacheDir, "exif_check_${System.currentTimeMillis()}.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            val exif = ExifInterface(tempFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            tempFile.delete()

            return when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            return 0
        }
    }
}
