package com.ourbloom.app.dashboard

import android.app.Dialog
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.ourbloom.app.R
import com.ourbloom.app.data.models.Memory
import java.io.File

fun Fragment.showLightbox(memory: Memory) {
    if (context == null) return

    val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.setContentView(R.layout.dialog_lightbox)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    val ivImage = dialog.findViewById<ImageView>(R.id.iv_lightbox_image)
    val btnClose = dialog.findViewById<ImageButton>(R.id.btn_close)
    val btnDownload = dialog.findViewById<ImageButton>(R.id.btn_download)
    val tvTitle = dialog.findViewById<TextView>(R.id.tv_lightbox_title)
    val tvDate = dialog.findViewById<TextView>(R.id.tv_lightbox_date)
    val btnPlay = dialog.findViewById<ImageButton>(R.id.btn_play_audio)

    tvTitle.text = memory.title
    tvDate.text = memory.dateStr

    if (memory.imageUrl.isNotEmpty()) {
        Glide.with(this)
            .load(memory.imageUrl)
            .placeholder(R.drawable.placeholder_memory)
            .into(ivImage)
    }

    // Robust Audio Setup
    var mediaPlayer: MediaPlayer? = null
    var isPrepared = false
    var isBuffering = false
    var isPlaying = false

    if (memory.audioUrl.isNotBlank()) {
        btnPlay.visibility = View.VISIBLE
        
        btnPlay.setOnClickListener {
            if (isBuffering) {
                Toast.makeText(context, "Buffering audio, please wait...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (mediaPlayer == null || !isPrepared) {
                try {
                    mediaPlayer?.release()
                    mediaPlayer = null
                    isBuffering = true
                    btnPlay.alpha = 0.6f
                    Toast.makeText(context, "Loading audio... 🎵", Toast.LENGTH_SHORT).show()

                    var finalUrl = memory.audioUrl.trim()
                    if (finalUrl.startsWith("/uploads")) {
                        finalUrl = "https://our-bloom.onrender.com" + finalUrl
                    }

                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(finalUrl)

                        setOnPreparedListener {
                            isPrepared = true
                            isBuffering = false
                            btnPlay.alpha = 1.0f
                            try {
                                start()
                                isPlaying = true
                                btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                            } catch (e: Exception) {
                                Log.e("LightboxHelper", "Error starting playback", e)
                                isPlaying = false
                                btnPlay.setImageResource(android.R.drawable.ic_media_play)
                            }
                        }

                        setOnCompletionListener {
                            isPlaying = false
                            btnPlay.setImageResource(android.R.drawable.ic_media_play)
                        }

                        setOnErrorListener { mp, what, extra ->
                            Log.e("LightboxHelper", "MediaPlayer error: what=$what, extra=$extra")
                            isPrepared = false
                            isBuffering = false
                            isPlaying = false
                            btnPlay.alpha = 1.0f
                            btnPlay.setImageResource(android.R.drawable.ic_media_play)
                            Toast.makeText(context, "Audio file is corrupt or unavailable", Toast.LENGTH_SHORT).show()
                            try {
                                mp.reset()
                            } catch (e: Exception) {
                                // ignore
                            }
                            true
                        }

                        prepareAsync()
                    }
                } catch (e: Exception) {
                    Log.e("LightboxHelper", "Exception initializing MediaPlayer", e)
                    isBuffering = false
                    isPrepared = false
                    btnPlay.alpha = 1.0f
                    Toast.makeText(context, "Cannot play audio", Toast.LENGTH_SHORT).show()
                }
            } else {
                try {
                    if (isPlaying) {
                        mediaPlayer?.pause()
                        isPlaying = false
                        btnPlay.setImageResource(android.R.drawable.ic_media_play)
                    } else {
                        mediaPlayer?.start()
                        isPlaying = true
                        btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                    }
                } catch (e: Exception) {
                    Log.e("LightboxHelper", "Exception toggling playback", e)
                    isPrepared = false
                    isPlaying = false
                    btnPlay.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        }
    }

    // Download/Save setup
    btnDownload.setOnClickListener {
        if (memory.imageUrl.isBlank()) return@setOnClickListener
        saveImageToGallery(requireContext(), ivImage.drawable, memory.imageUrl)
    }

    btnClose.setOnClickListener {
        dialog.dismiss()
    }

    dialog.setOnDismissListener {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    dialog.show()
}

/**
 * Fullscreen Lightbox for Chat Images with Save-to-Gallery option
 */
fun Fragment.showChatImageLightbox(
    imageUrl: String,
    senderName: String? = null,
    timeStr: String? = null
) {
    if (context == null || imageUrl.isBlank()) return

    val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.setContentView(R.layout.dialog_lightbox)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    val ivImage = dialog.findViewById<ImageView>(R.id.iv_lightbox_image)
    val btnClose = dialog.findViewById<ImageButton>(R.id.btn_close)
    val btnDownload = dialog.findViewById<ImageButton>(R.id.btn_download)
    val tvTitle = dialog.findViewById<TextView>(R.id.tv_lightbox_title)
    val tvDate = dialog.findViewById<TextView>(R.id.tv_lightbox_date)
    val btnPlay = dialog.findViewById<ImageButton>(R.id.btn_play_audio)
    val detailsLayout = dialog.findViewById<View>(R.id.ll_lightbox_details)

    btnPlay?.visibility = View.GONE

    if (!senderName.isNullOrBlank() || !timeStr.isNullOrBlank()) {
        detailsLayout?.visibility = View.VISIBLE
        tvTitle?.text = senderName ?: ""
        tvDate?.text = timeStr ?: ""
        tvTitle?.visibility = if (senderName.isNullOrBlank()) View.GONE else View.VISIBLE
        tvDate?.visibility = if (timeStr.isNullOrBlank()) View.GONE else View.VISIBLE
    } else {
        detailsLayout?.visibility = View.GONE
    }

    Glide.with(this)
        .load(imageUrl)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .placeholder(R.drawable.placeholder_memory)
        .into(ivImage)

    // Smooth Pinch-to-Zoom, Pan, and Double-Tap Zoom
    var scaleFactor = 1.0f

    val scaleDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(1.0f, 4.5f)
            ivImage.scaleX = scaleFactor
            ivImage.scaleY = scaleFactor
            return true
        }
    })

    val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (detailsLayout != null && (!senderName.isNullOrBlank() || !timeStr.isNullOrBlank())) {
                detailsLayout.visibility = if (detailsLayout.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scaleFactor > 1.0f) {
                scaleFactor = 1.0f
                ivImage.animate().scaleX(1.0f).scaleY(1.0f).translationX(0f).translationY(0f).setDuration(200).start()
            } else {
                scaleFactor = 2.5f
                ivImage.animate().scaleX(2.5f).scaleY(2.5f).setDuration(200).start()
            }
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scaleFactor > 1.0f) {
                ivImage.translationX -= distanceX
                ivImage.translationY -= distanceY
                return true
            }
            return false
        }
    })

    ivImage.setOnTouchListener { _, event ->
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && scaleFactor <= 1.0f) {
            ivImage.animate().translationX(0f).translationY(0f).setDuration(150).start()
        }
        true
    }

    btnDownload.setOnClickListener {
        saveImageToGallery(requireContext(), ivImage.drawable, imageUrl)
    }

    btnClose.setOnClickListener {
        dialog.dismiss()
    }

    dialog.show()
}

/**
 * Saves the given image to the device's public Pictures/OurBloom gallery.
 * Prioritizes copying the 100% original full-resolution file from Glide cache/network,
 * with fallback to bitmap MediaStore insertion and DownloadManager.
 */
private fun saveImageToGallery(context: Context, drawable: Drawable?, fallbackUrl: String) {
    var fullUrl = fallbackUrl.trim()
    if (fullUrl.startsWith("/uploads")) {
        fullUrl = "https://our-bloom.onrender.com$fullUrl"
    }

    // 1. Try to copy the 100% original full-resolution file from Glide cache or network
    try {
        Glide.with(context.applicationContext)
            .asFile()
            .load(fullUrl)
            .into(object : CustomTarget<File>() {
                override fun onResourceReady(file: File, transition: Transition<in File>?) {
                    try {
                        val filename = "OurBloom_${System.currentTimeMillis()}.jpg"
                        val resolver = context.contentResolver
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "OurBloom")
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                        }

                        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { out ->
                                file.inputStream().use { input -> input.copyTo(out) }
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                contentValues.clear()
                                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                                resolver.update(uri, contentValues, null, null)
                            }
                            Toast.makeText(context, "Full-resolution photo saved to Gallery! 🌸", Toast.LENGTH_SHORT).show()
                            return
                        }
                    } catch (e: Exception) {
                        Log.e("LightboxHelper", "Failed writing cached file to MediaStore: ${e.message}")
                    }
                    saveBitmapFallback(context, drawable, fullUrl)
                }

                override fun onLoadCleared(placeholder: Drawable?) {}

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    saveBitmapFallback(context, drawable, fullUrl)
                }
            })
        return
    } catch (e: Exception) {
        Log.e("LightboxHelper", "Error requesting original file from Glide: ${e.message}")
    }

    saveBitmapFallback(context, drawable, fullUrl)
}

private fun saveBitmapFallback(context: Context, drawable: Drawable?, fallbackUrl: String) {
    try {
        val bitmap = (drawable as? BitmapDrawable)?.bitmap
        if (bitmap != null) {
            val filename = "OurBloom_${System.currentTimeMillis()}.jpg"
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "OurBloom")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                Toast.makeText(context, "Photo saved to Gallery! 🌸", Toast.LENGTH_SHORT).show()
                return
            }
        }
    } catch (e: Exception) {
        Log.e("LightboxHelper", "Error saving cached bitmap to MediaStore: ${e.message}")
    }

    // Fallback to DownloadManager
    try {
        var downloadUrl = fallbackUrl.trim()
        if (downloadUrl.startsWith("/uploads")) {
            downloadUrl = "https://our-bloom.onrender.com$downloadUrl"
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            setTitle("Our Bloom Photo")
            setDescription("Saving photo to Gallery...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_PICTURES,
                "OurBloom_${System.currentTimeMillis()}.jpg"
            )
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, "Saving photo to gallery... 🌸", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("LightboxHelper", "Failed to enqueue download", e)
        Toast.makeText(context, "Failed to save photo: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
