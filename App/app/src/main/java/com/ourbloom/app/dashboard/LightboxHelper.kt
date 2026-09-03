package com.ourbloom.app.dashboard

import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.ourbloom.app.R
import com.ourbloom.app.data.models.Memory

fun Fragment.showLightbox(memory: Memory) {
    if (context == null) return

    val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.setContentView(R.layout.dialog_lightbox)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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
                            true // Consumes error so MediaPlayer doesn't crash or cascade to state 0
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

    // Download setup
    btnDownload.setOnClickListener {
        if (memory.imageUrl.isBlank()) return@setOnClickListener
        
        try {
            val request = DownloadManager.Request(Uri.parse(memory.imageUrl))
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            request.setTitle("Our Bloom Memory")
            request.setDescription("Downloading photo...")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "bloom_memory_${System.currentTimeMillis()}.jpg")

            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(context, "Downloading photo...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to download", Toast.LENGTH_SHORT).show()
        }
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
