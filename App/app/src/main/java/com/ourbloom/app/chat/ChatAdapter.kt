package com.ourbloom.app.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ourbloom.app.R
import com.ourbloom.app.data.models.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.widget.ImageViewCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class ChatAdapter(
    private val currentUserId: String,
    private val onImageClick: (ChatMessage) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val COLOR_TICK_READ = 0xFF34B7F1.toInt() // WhatsApp cyan blue
        private const val COLOR_TICK_DEFAULT = 0xFFE0E0E0.toInt() // Subtle grey/white
    }

    private val messages = mutableListOf<ChatMessage>()
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    var partnerAvatarUrl: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var selectedMessageId: String? = null
        private set

    var highlightedMessageId: String? = null
        private set

    fun flashHighlightMessage(messageId: String) {
        val oldHighlighted = highlightedMessageId
        highlightedMessageId = messageId
        if (oldHighlighted != null) {
            val oldIdx = messages.indexOfFirst { it.id == oldHighlighted }
            if (oldIdx != -1) notifyItemChanged(oldIdx)
        }
        val newIdx = messages.indexOfFirst { it.id == messageId }
        if (newIdx != -1) {
            notifyItemChanged(newIdx)
            Handler(Looper.getMainLooper()).postDelayed({
                if (highlightedMessageId == messageId) {
                    highlightedMessageId = null
                    val idx = messages.indexOfFirst { it.id == messageId }
                    if (idx != -1) notifyItemChanged(idx)
                }
            }, 1200)
        }
    }

    var onMessageLongClick: ((ChatMessage) -> Unit)? = null
    var onMessageClick: ((ChatMessage) -> Unit)? = null
    var onQuoteClick: ((String) -> Unit)? = null

    // Audio Playback State
    private var mediaPlayer: MediaPlayer? = null
    private var playingMessageId: String? = null
    private var isPlayerPlaying: Boolean = false
    private var isPlayerPreparing: Boolean = false
    private var currentPlaybackProgress: Float = 0f
    private var currentPlaybackMs: Long = 0L
    private var currentPlaybackSpeed: Float = 1.0f
    private var activeWaveformView: VoiceWaveformView? = null
    private var activeDurationText: TextView? = null
    private var activePlayButton: ImageView? = null
    private var activeSpeedText: TextView? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun releaseAudioPlayer() {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        // Reset previous active views
        activePlayButton?.alpha = 1.0f
        activePlayButton?.setImageResource(R.drawable.ic_play_arrow)
        activeWaveformView?.progress = 0f
        activeSpeedText?.text = "1x"

        playingMessageId = null
        isPlayerPlaying = false
        isPlayerPreparing = false
        currentPlaybackProgress = 0f
        currentPlaybackMs = 0L
        currentPlaybackSpeed = 1.0f
        activeWaveformView = null
        activeDurationText = null
        activePlayButton = null
        activeSpeedText = null
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        releaseAudioPlayer()
        adapterScope.cancel()
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val seconds = ms / 1000
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun applyPlaybackSpeed(player: MediaPlayer?, speed: Float) {
        if (player == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = player.playbackParams ?: android.media.PlaybackParams()
                params.speed = speed
                player.playbackParams = params
            } catch (e: Exception) {
                Log.e("ChatAdapter", "Failed to set playback speed", e)
            }
        }
    }

    private fun cyclePlaybackSpeed(speedTv: TextView) {
        currentPlaybackSpeed = when (currentPlaybackSpeed) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        val label = if (currentPlaybackSpeed == 1.0f) "1x" else if (currentPlaybackSpeed == 1.5f) "1.5x" else "2x"
        speedTv.text = label
        mediaPlayer?.let { applyPlaybackSpeed(it, currentPlaybackSpeed) }
    }

    private fun pausePlayback() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
            }
        } catch (_: Exception) {}
        isPlayerPlaying = false
        activePlayButton?.alpha = 1.0f
        activePlayButton?.setImageResource(R.drawable.ic_play_arrow)
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
    }

    private fun resumePlayback() {
        val player = mediaPlayer ?: return
        try {
            applyPlaybackSpeed(player, currentPlaybackSpeed)
            player.start()
            isPlayerPlaying = true
            activePlayButton?.alpha = 1.0f
            activePlayButton?.setImageResource(R.drawable.ic_pause)
            startProgressTracker()
        } catch (e: Exception) {
            Log.e("ChatAdapter", "Error resuming playback", e)
            onPlaybackFinished()
        }
    }

    private fun onPlaybackFinished(message: ChatMessage? = null) {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isPlayerPlaying = false
        isPlayerPreparing = false
        currentPlaybackProgress = 0f
        currentPlaybackMs = 0L

        activePlayButton?.alpha = 1.0f
        activePlayButton?.setImageResource(R.drawable.ic_play_arrow)
        activeWaveformView?.progress = 0f
        activeSpeedText?.text = "1x"
        currentPlaybackSpeed = 1.0f

        if (message != null) {
            activeDurationText?.text = formatDuration(message.audioDurationMs ?: 0L)
        } else {
            activeDurationText?.text = "0:00"
        }

        playingMessageId = null
        activePlayButton = null
        activeWaveformView = null
        activeDurationText = null
        activeSpeedText = null
    }

    private fun toggleAudioPlayback(
        context: Context,
        message: ChatMessage,
        playBtn: ImageView,
        waveformView: VoiceWaveformView,
        durationTv: TextView,
        speedTv: TextView
    ) {
        val audioUrl = message.audioUrl ?: return

        // 1. If tapping on the currently active message:
        if (playingMessageId == message.id) {
            if (isPlayerPreparing) {
                // User cancelled while loading
                releaseAudioPlayer()
                return
            }
            if (isPlayerPlaying) {
                pausePlayback()
            } else {
                resumePlayback()
            }
            return
        }

        // 2. Switching to a new message: Release previous player completely
        releaseAudioPlayer()

        playingMessageId = message.id
        isPlayerPreparing = true
        isPlayerPlaying = false
        activePlayButton = playBtn
        activeWaveformView = waveformView
        activeDurationText = durationTv
        activeSpeedText = speedTv
        currentPlaybackSpeed = 1.0f
        currentPlaybackProgress = 0f
        currentPlaybackMs = 0L
        speedTv.text = "1x"

        // Visual loading state
        playBtn.alpha = 0.6f
        playBtn.setImageResource(R.drawable.ic_pause)

        adapterScope.launch {
            val localFile = AudioCacheManager.getOrDownloadAudio(context, audioUrl)

            // Ensure this message is still the intended message to play
            if (playingMessageId != message.id) return@launch

            if (localFile == null || !localFile.exists() || localFile.length() < 500) {
                Toast.makeText(context, "Could not load voice note. Check connection.", Toast.LENGTH_SHORT).show()
                onPlaybackFinished(message)
                return@launch
            }

            playBtn.alpha = 1.0f
            startMediaPlayer(context, message, localFile)
        }
    }

    private fun startMediaPlayer(context: Context, message: ChatMessage, file: File) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            // Request Audio Focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            pausePlayback()
                        }
                    }
                    .build()
                audioManager?.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            pausePlayback()
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }

            val player = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    if (playingMessageId != message.id) {
                        try { mp.release() } catch (_: Exception) {}
                        return@setOnPreparedListener
                    }
                    isPlayerPreparing = false
                    isPlayerPlaying = true
                    activePlayButton?.alpha = 1.0f
                    activePlayButton?.setImageResource(R.drawable.ic_pause)
                    applyPlaybackSpeed(mp, currentPlaybackSpeed)
                    mp.start()
                    startProgressTracker()
                }
                setOnCompletionListener {
                    onPlaybackFinished(message)
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("ChatAdapter", "MediaPlayer error: what=$what extra=$extra")
                    onPlaybackFinished(message)
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e("ChatAdapter", "Error starting MediaPlayer", e)
            Toast.makeText(context, "Cannot play voice note", Toast.LENGTH_SHORT).show()
            onPlaybackFinished(message)
        }
    }

    private fun startProgressTracker() {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        progressRunnable = object : Runnable {
            override fun run() {
                val player = mediaPlayer
                if (player != null && isPlayerPlaying) {
                    try {
                        if (player.isPlaying) {
                            val current = player.currentPosition
                            val total = player.duration
                            currentPlaybackMs = current.toLong()
                            if (total > 0) {
                                currentPlaybackProgress = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                activeWaveformView?.progress = currentPlaybackProgress
                            }
                            val seconds = current / 1000
                            activeDurationText?.text = String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
                            progressHandler.postDelayed(this, 60)
                        }
                    } catch (e: Exception) {
                        Log.w("ChatAdapter", "Progress tracker error: ${e.message}")
                    }
                }
            }
        }
        progressRunnable?.let { progressHandler.post(it) }
    }

    private fun bindVoiceNote(
        message: ChatMessage,
        layoutAudio: View?,
        waveformAudio: VoiceWaveformView?,
        ivPlayPause: ImageView?,
        tvAudioDuration: TextView?,
        tvAudioSpeed: TextView?,
        isSent: Boolean
    ) {
        if (!message.audioUrl.isNullOrBlank()) {
            layoutAudio?.visibility = View.VISIBLE
            if (isSent) {
                waveformAudio?.playedColor = 0xFFFFFFFF.toInt()
                waveformAudio?.unplayedColor = 0x4DFFFFFF.toInt()
            } else {
                waveformAudio?.playedColor = 0xFFE85D75.toInt()
                waveformAudio?.unplayedColor = 0x33E85D75.toInt()
            }
            waveformAudio?.setWaveformSeed(message.id)

            val isThisActive = message.id == playingMessageId
            if (isThisActive) {
                // Re-bind active views so animations and progress tracker update current ViewHolder
                activePlayButton = ivPlayPause
                activeWaveformView = waveformAudio
                activeDurationText = tvAudioDuration
                activeSpeedText = tvAudioSpeed

                if (isPlayerPreparing) {
                    ivPlayPause?.alpha = 0.6f
                    ivPlayPause?.setImageResource(R.drawable.ic_pause)
                } else if (isPlayerPlaying) {
                    ivPlayPause?.alpha = 1.0f
                    ivPlayPause?.setImageResource(R.drawable.ic_pause)
                } else {
                    ivPlayPause?.alpha = 1.0f
                    ivPlayPause?.setImageResource(R.drawable.ic_play_arrow)
                }

                val speedLabel = if (currentPlaybackSpeed == 1.0f) "1x" else if (currentPlaybackSpeed == 1.5f) "1.5x" else "2x"
                tvAudioSpeed?.text = speedLabel
                waveformAudio?.progress = currentPlaybackProgress

                if (currentPlaybackMs > 0) {
                    val seconds = currentPlaybackMs / 1000
                    tvAudioDuration?.text = String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
                } else {
                    tvAudioDuration?.text = formatDuration(message.audioDurationMs ?: 0L)
                }
            } else {
                ivPlayPause?.alpha = 1.0f
                ivPlayPause?.setImageResource(R.drawable.ic_play_arrow)
                waveformAudio?.progress = 0f
                tvAudioDuration?.text = formatDuration(message.audioDurationMs ?: 0L)
                tvAudioSpeed?.text = "1x"
            }

            ivPlayPause?.setOnClickListener {
                val wf = waveformAudio ?: return@setOnClickListener
                val tv = tvAudioDuration ?: return@setOnClickListener
                val sp = tvAudioSpeed ?: return@setOnClickListener
                toggleAudioPlayback(it.context, message, ivPlayPause, wf, tv, sp)
            }

            tvAudioSpeed?.setOnClickListener {
                if (playingMessageId == message.id) {
                    cyclePlaybackSpeed(tvAudioSpeed)
                }
            }

            waveformAudio?.onSeekListener = { seekProgress ->
                if (playingMessageId == message.id) {
                    val mp = mediaPlayer
                    if (mp != null) {
                        try {
                            val total = mp.duration
                            if (total > 0) {
                                val target = (seekProgress * total).toInt()
                                mp.seekTo(target)
                                currentPlaybackProgress = seekProgress
                                currentPlaybackMs = target.toLong()
                                val seconds = target / 1000
                                tvAudioDuration?.text = String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
                            }
                        } catch (e: Exception) {
                            Log.w("ChatAdapter", "Seek error: ${e.message}")
                        }
                    }
                }
            }
        } else {
            layoutAudio?.visibility = View.GONE
        }
    }

    fun submitList(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun setSelectedMessage(id: String?) {
        val oldId = selectedMessageId
        selectedMessageId = id
        if (oldId != null) {
            val oldIdx = messages.indexOfFirst { it.id == oldId }
            if (oldIdx != -1) notifyItemChanged(oldIdx)
        }
        if (id != null) {
            val newIdx = messages.indexOfFirst { it.id == id }
            if (newIdx != -1) notifyItemChanged(newIdx)
        }
    }

    fun getSelectedMessage(): ChatMessage? = messages.find { it.id == selectedMessageId }

    fun getMessagePosition(messageId: String): Int = messages.indexOfFirst { it.id == messageId }

    fun getMessageAt(position: Int): ChatMessage? = messages.getOrNull(position)

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is SentMessageViewHolder) {
            holder.bind(message)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.bind(message, partnerAvatarUrl)
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rootLayout: View = itemView.findViewById(R.id.layout_message_root)
        private val bubbleContainer: View? = itemView.findViewById(R.id.layout_bubble_container)
        private val tvText: TextView = itemView.findViewById(R.id.tv_chat_text)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_chat_time)
        private val ivStatus: ImageView = itemView.findViewById(R.id.iv_chat_status)
        private val cardImage: View = itemView.findViewById(R.id.card_chat_image)
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_chat_image)
        private val layoutQuote: View? = itemView.findViewById(R.id.layout_quote_preview)
        private val viewQuoteStripe: View? = itemView.findViewById(R.id.view_quote_stripe)
        private val tvQuoteSender: TextView? = itemView.findViewById(R.id.tv_quote_sender)
        private val tvQuoteText: TextView? = itemView.findViewById(R.id.tv_quote_text)
        private val cardQuoteThumb: View? = itemView.findViewById(R.id.card_quote_thumb)
        private val ivQuoteThumb: ImageView? = itemView.findViewById(R.id.iv_quote_thumb)
        private val layoutAudio: View? = itemView.findViewById(R.id.layout_chat_audio)
        private val ivPlayPause: ImageView? = itemView.findViewById(R.id.iv_audio_play_pause)
        private val waveformAudio: VoiceWaveformView? = itemView.findViewById(R.id.waveform_audio)
        private val tvAudioDuration: TextView? = itemView.findViewById(R.id.tv_audio_duration)
        private val tvAudioSpeed: TextView? = itemView.findViewById(R.id.tv_audio_speed)

        fun bind(message: ChatMessage) {
            val isSelected = message.id == selectedMessageId
            val isHighlighted = message.id == highlightedMessageId
            if (isSelected) {
                rootLayout.setBackgroundResource(R.drawable.bg_msg_selected)
            } else if (isHighlighted) {
                rootLayout.setBackgroundColor(0x35E85D75.toInt())
            } else {
                rootLayout.setBackgroundResource(0)
            }

            val longClickListener = View.OnLongClickListener {
                onMessageLongClick?.invoke(message)
                true
            }
            val clickListener = View.OnClickListener {
                onMessageClick?.invoke(message)
            }

            itemView.setOnLongClickListener(longClickListener)
            itemView.setOnClickListener(clickListener)
            rootLayout.setOnLongClickListener(longClickListener)
            rootLayout.setOnClickListener(clickListener)
            bubbleContainer?.setOnLongClickListener(longClickListener)
            bubbleContainer?.setOnClickListener(clickListener)

            // Quoted reply binding (WhatsApp Style)
            if (message.isReply) {
                layoutQuote?.visibility = View.VISIBLE
                val isSenderYou = message.replyToSenderName.isNullOrBlank() || message.replyToSenderName == "You"
                val senderLabel = if (isSenderYou) "You" else message.replyToSenderName ?: "Partner"
                tvQuoteSender?.text = senderLabel
                tvQuoteText?.text = message.replyToText ?: ""

                val accentColor = if (isSenderYou) 0xFFFF859A.toInt() else 0xFF25D366.toInt()
                viewQuoteStripe?.setBackgroundColor(accentColor)
                tvQuoteSender?.setTextColor(accentColor)

                val quoteImgUrl = message.replyToImageUrl?.takeIf { it.isNotBlank() }
                    ?: messages.find { it.id == message.replyToId }?.imageUrl?.takeIf { it.isNotBlank() }

                if (!quoteImgUrl.isNullOrBlank() && cardQuoteThumb != null && ivQuoteThumb != null) {
                    cardQuoteThumb.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(quoteImgUrl)
                        .centerCrop()
                        .into(ivQuoteThumb)
                } else {
                    cardQuoteThumb?.visibility = View.GONE
                }

                layoutQuote?.setOnClickListener {
                    val targetId = message.replyToId
                    if (!targetId.isNullOrBlank()) {
                        onQuoteClick?.invoke(targetId)
                    }
                }
            } else {
                layoutQuote?.visibility = View.GONE
            }

            // Voice note binding
            bindVoiceNote(
                message = message,
                layoutAudio = layoutAudio,
                waveformAudio = waveformAudio,
                ivPlayPause = ivPlayPause,
                tvAudioDuration = tvAudioDuration,
                tvAudioSpeed = tvAudioSpeed,
                isSent = true
            )

            if (message.text.isBlank() || message.text == "🎙️ Voice note" || message.text == "🎙️ Voice message") {
                tvText.visibility = View.GONE
            } else {
                tvText.text = message.text
                tvText.visibility = View.VISIBLE
            }

            tvTime.text = timeFormat.format(Date(message.timestamp))

            // WhatsApp-style status ticks:
            // 1. Double Blue Tick: read by receiver
            // 2. Double Grey Tick: delivered to receiver's device
            // 3. Single Grey Tick: sent to server, receiver not yet received
            when {
                message.isSeen -> {
                    ivStatus.setImageResource(R.drawable.ic_msg_status_double_tick)
                    ImageViewCompat.setImageTintList(ivStatus, ColorStateList.valueOf(COLOR_TICK_READ))
                    ImageViewCompat.setImageTintMode(ivStatus, PorterDuff.Mode.SRC_IN)
                    ivStatus.contentDescription = "Read"
                }
                message.hasDelivered -> {
                    ivStatus.setImageResource(R.drawable.ic_msg_status_double_tick)
                    ImageViewCompat.setImageTintList(ivStatus, ColorStateList.valueOf(COLOR_TICK_DEFAULT))
                    ImageViewCompat.setImageTintMode(ivStatus, PorterDuff.Mode.SRC_IN)
                    ivStatus.contentDescription = "Delivered"
                }
                else -> {
                    ivStatus.setImageResource(R.drawable.ic_msg_status_single_tick)
                    ImageViewCompat.setImageTintList(ivStatus, ColorStateList.valueOf(COLOR_TICK_DEFAULT))
                    ImageViewCompat.setImageTintMode(ivStatus, PorterDuff.Mode.SRC_IN)
                    ivStatus.contentDescription = "Sent"
                }
            }

            if (!message.imageUrl.isNullOrBlank()) {
                cardImage.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(message.imageUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.placeholder_memory)
                    .error(R.drawable.placeholder_memory)
                    .centerCrop()
                    .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(
                            e: com.bumptech.glide.load.engine.GlideException?,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            android.util.Log.e("ChatAdapter", "Failed to load sent chat image: $model", e)
                            return false
                        }
                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable,
                            model: Any,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                            dataSource: com.bumptech.glide.load.DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            return false
                        }
                    })
                    .into(ivImage)

                val imageClickListener = View.OnClickListener {
                    if (selectedMessageId != null) {
                        onMessageClick?.invoke(message)
                    } else {
                        onImageClick(message)
                    }
                }
                ivImage.setOnClickListener(imageClickListener)
                cardImage.setOnClickListener(imageClickListener)
                ivImage.setOnLongClickListener(longClickListener)
                cardImage.setOnLongClickListener(longClickListener)
            } else {
                cardImage.visibility = View.GONE
            }
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rootLayout: View = itemView.findViewById(R.id.layout_message_root)
        private val bubbleContainer: View? = itemView.findViewById(R.id.layout_bubble_container)
        private val tvSender: TextView = itemView.findViewById(R.id.tv_chat_sender)
        private val tvText: TextView = itemView.findViewById(R.id.tv_chat_text)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_chat_time)
        private val cardImage: View = itemView.findViewById(R.id.card_chat_image)
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_chat_image)
        private val ivPartnerAvatar: ImageView = itemView.findViewById(R.id.iv_chat_partner_avatar)
        private val layoutQuote: View? = itemView.findViewById(R.id.layout_quote_preview)
        private val viewQuoteStripe: View? = itemView.findViewById(R.id.view_quote_stripe)
        private val tvQuoteSender: TextView? = itemView.findViewById(R.id.tv_quote_sender)
        private val tvQuoteText: TextView? = itemView.findViewById(R.id.tv_quote_text)
        private val cardQuoteThumb: View? = itemView.findViewById(R.id.card_quote_thumb)
        private val ivQuoteThumb: ImageView? = itemView.findViewById(R.id.iv_quote_thumb)
        private val layoutAudio: View? = itemView.findViewById(R.id.layout_chat_audio)
        private val ivPlayPause: ImageView? = itemView.findViewById(R.id.iv_audio_play_pause)
        private val waveformAudio: VoiceWaveformView? = itemView.findViewById(R.id.waveform_audio)
        private val tvAudioDuration: TextView? = itemView.findViewById(R.id.tv_audio_duration)
        private val tvAudioSpeed: TextView? = itemView.findViewById(R.id.tv_audio_speed)

        fun bind(message: ChatMessage, partnerAvatarUrl: String?) {
            val isSelected = message.id == selectedMessageId
            val isHighlighted = message.id == highlightedMessageId
            if (isSelected) {
                rootLayout.setBackgroundResource(R.drawable.bg_msg_selected)
            } else if (isHighlighted) {
                rootLayout.setBackgroundColor(0x35E85D75.toInt())
            } else {
                rootLayout.setBackgroundResource(0)
            }

            val longClickListener = View.OnLongClickListener {
                onMessageLongClick?.invoke(message)
                true
            }
            val clickListener = View.OnClickListener {
                onMessageClick?.invoke(message)
            }

            itemView.setOnLongClickListener(longClickListener)
            itemView.setOnClickListener(clickListener)
            rootLayout.setOnLongClickListener(longClickListener)
            rootLayout.setOnClickListener(clickListener)
            bubbleContainer?.setOnLongClickListener(longClickListener)
            bubbleContainer?.setOnClickListener(clickListener)

            // Quoted reply binding (WhatsApp Style)
            if (message.isReply) {
                layoutQuote?.visibility = View.VISIBLE
                val isSenderYou = message.replyToSenderName == "You"
                val senderLabel = if (isSenderYou) "You" else message.replyToSenderName ?: "Partner"
                tvQuoteSender?.text = senderLabel
                tvQuoteText?.text = message.replyToText ?: ""

                // WhatsApp dynamic quote colors:
                // If replying to You -> Rose (#E85D75)
                // If replying to Partner -> WhatsApp Emerald Green (#00A884)
                val accentColor = if (isSenderYou) 0xFFE85D75.toInt() else 0xFF00A884.toInt()
                viewQuoteStripe?.setBackgroundColor(accentColor)
                tvQuoteSender?.setTextColor(accentColor)

                val quoteImgUrl = message.replyToImageUrl?.takeIf { it.isNotBlank() }
                    ?: messages.find { it.id == message.replyToId }?.imageUrl?.takeIf { it.isNotBlank() }

                if (!quoteImgUrl.isNullOrBlank() && cardQuoteThumb != null && ivQuoteThumb != null) {
                    cardQuoteThumb.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(quoteImgUrl)
                        .centerCrop()
                        .into(ivQuoteThumb)
                } else {
                    cardQuoteThumb?.visibility = View.GONE
                }

                layoutQuote?.setOnClickListener {
                    val targetId = message.replyToId
                    if (!targetId.isNullOrBlank()) {
                        onQuoteClick?.invoke(targetId)
                    }
                }
            } else {
                layoutQuote?.visibility = View.GONE
            }

            tvSender.text = message.senderName.ifBlank { "My Love" }

            if (!partnerAvatarUrl.isNullOrBlank()) {
                Glide.with(itemView.context)
                    .load(partnerAvatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_favorite)
                    .into(ivPartnerAvatar)
                ivPartnerAvatar.imageTintList = null
                ivPartnerAvatar.setPadding(0, 0, 0, 0)
            } else {
                ivPartnerAvatar.setImageResource(R.drawable.ic_favorite)
                ivPartnerAvatar.imageTintList = ColorStateList.valueOf(0xFFE85D75.toInt())
                val p = (5 * itemView.context.resources.displayMetrics.density).toInt()
                ivPartnerAvatar.setPadding(p, p, p, p)
            }

            // Voice note binding
            bindVoiceNote(
                message = message,
                layoutAudio = layoutAudio,
                waveformAudio = waveformAudio,
                ivPlayPause = ivPlayPause,
                tvAudioDuration = tvAudioDuration,
                tvAudioSpeed = tvAudioSpeed,
                isSent = false
            )

            if (message.text.isBlank() || message.text == "🎙️ Voice note" || message.text == "🎙️ Voice message") {
                tvText.visibility = View.GONE
            } else {
                tvText.text = message.text
                tvText.visibility = View.VISIBLE
            }

            tvTime.text = timeFormat.format(Date(message.timestamp))

            if (!message.imageUrl.isNullOrBlank()) {
                cardImage.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(message.imageUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.placeholder_memory)
                    .error(R.drawable.placeholder_memory)
                    .centerCrop()
                    .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(
                            e: com.bumptech.glide.load.engine.GlideException?,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            android.util.Log.e("ChatAdapter", "Failed to load received chat image: $model", e)
                            return false
                        }
                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable,
                            model: Any,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                            dataSource: com.bumptech.glide.load.DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            return false
                        }
                    })
                    .into(ivImage)

                val imageClickListener = View.OnClickListener {
                    if (selectedMessageId != null) {
                        onMessageClick?.invoke(message)
                    } else {
                        onImageClick(message)
                    }
                }
                ivImage.setOnClickListener(imageClickListener)
                cardImage.setOnClickListener(imageClickListener)
                ivImage.setOnLongClickListener(longClickListener)
                cardImage.setOnLongClickListener(longClickListener)
            } else {
                cardImage.visibility = View.GONE
            }
        }
    }
}
