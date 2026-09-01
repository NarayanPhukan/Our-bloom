package com.ourbloom.app.dashboard

import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
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
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#D9000000"))) // 85% opacity black
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

    // Audio setup
    var mediaPlayer: MediaPlayer? = null
    var isPlaying = false

    if (memory.audioUrl.isNotBlank()) {
        btnPlay.visibility = View.VISIBLE
        
        btnPlay.setOnClickListener {
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = MediaPlayer().apply {
                        var finalUrl = memory.audioUrl
                        if (finalUrl.startsWith("/uploads")) {
                            finalUrl = "http://10.0.2.2:5000" + finalUrl // Emulator fallback
                        }
                        setDataSource(finalUrl)
                        prepareAsync()
                        setOnPreparedListener {
                            start()
                            isPlaying = true
                            btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                        }
                        setOnCompletionListener {
                            isPlaying = false
                            btnPlay.setImageResource(android.R.drawable.ic_media_play)
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot play audio", Toast.LENGTH_SHORT).show()
                }
            } else {
                if (isPlaying) {
                    mediaPlayer?.pause()
                    isPlaying = false
                    btnPlay.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    mediaPlayer?.start()
                    isPlaying = true
                    btnPlay.setImageResource(android.R.drawable.ic_media_pause)
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
