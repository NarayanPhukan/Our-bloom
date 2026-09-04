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

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import androidx.core.widget.ImageViewCompat

class ChatAdapter(
    private val currentUserId: String,
    private val onImageClick: (String) -> Unit = {}
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

    var onMessageLongClick: ((ChatMessage) -> Unit)? = null
    var onMessageClick: ((ChatMessage) -> Unit)? = null
    var onQuoteClick: ((String) -> Unit)? = null

    // Audio Playback State
    private var mediaPlayer: MediaPlayer? = null
    private var playingMessageId: String? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    private var activeProgressBar: ProgressBar? = null
    private var activeDurationText: TextView? = null
    private var activePlayButton: ImageView? = null

    fun releaseAudioPlayer() {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        playingMessageId = null
        activeProgressBar = null
        activeDurationText = null
        activePlayButton = null
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        releaseAudioPlayer()
    }

    private fun toggleAudioPlayback(
        message: ChatMessage,
        playBtn: ImageView,
        progressBar: ProgressBar,
        durationTv: TextView
    ) {
        val audioUrl = message.audioUrl ?: return

        if (playingMessageId == message.id) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    playBtn.setImageResource(R.drawable.ic_play_arrow)
                    progressRunnable?.let { progressHandler.removeCallbacks(it) }
                } else {
                    player.start()
                    playBtn.setImageResource(R.drawable.ic_pause)
                    startProgressTracker()
                }
            }
            return
        }

        releaseAudioPlayer()

        playingMessageId = message.id
        activePlayButton = playBtn
        activeProgressBar = progressBar
        activeDurationText = durationTv

        playBtn.setImageResource(R.drawable.ic_pause)

        try {
            val player = MediaPlayer().apply {
                setDataSource(audioUrl)
                setOnPreparedListener { mp ->
                    mp.start()
                    val totalDuration = mp.duration
                    progressBar.max = if (totalDuration > 0) totalDuration else 100
                    startProgressTracker()
                }
                setOnCompletionListener {
                    playBtn.setImageResource(R.drawable.ic_play_arrow)
                    progressBar.progress = 0
                    durationTv.text = "0:00"
                    releaseAudioPlayer()
                }
                setOnErrorListener { _, _, _ ->
                    playBtn.setImageResource(R.drawable.ic_play_arrow)
                    progressBar.progress = 0
                    durationTv.text = "0:00"
                    releaseAudioPlayer()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            playBtn.setImageResource(R.drawable.ic_play_arrow)
            releaseAudioPlayer()
        }
    }

    private fun startProgressTracker() {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        progressRunnable = object : Runnable {
            override fun run() {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    val current = player.currentPosition
                    activeProgressBar?.progress = current
                    val seconds = current / 1000
                    activeDurationText?.text = String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
                    progressHandler.postDelayed(this, 150)
                }
            }
        }
        progressRunnable?.let { progressHandler.post(it) }
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
        private val tvQuoteSender: TextView? = itemView.findViewById(R.id.tv_quote_sender)
        private val tvQuoteText: TextView? = itemView.findViewById(R.id.tv_quote_text)
        private val layoutAudio: View? = itemView.findViewById(R.id.layout_chat_audio)
        private val ivPlayPause: ImageView? = itemView.findViewById(R.id.iv_audio_play_pause)
        private val pbAudioProgress: ProgressBar? = itemView.findViewById(R.id.pb_audio_progress)
        private val tvAudioDuration: TextView? = itemView.findViewById(R.id.tv_audio_duration)

        fun bind(message: ChatMessage) {
            val isSelected = message.id == selectedMessageId
            rootLayout.setBackgroundResource(if (isSelected) R.drawable.bg_msg_selected else 0)

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

            // Quoted reply binding
            if (message.isReply) {
                layoutQuote?.visibility = View.VISIBLE
                tvQuoteSender?.text = message.replyToSenderName?.ifBlank { "You" } ?: "You"
                tvQuoteText?.text = message.replyToText ?: ""
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
            if (!message.audioUrl.isNullOrBlank()) {
                layoutAudio?.visibility = View.VISIBLE
                val isSelfPlaying = message.id == playingMessageId && mediaPlayer?.isPlaying == true
                ivPlayPause?.setImageResource(if (isSelfPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)

                if (!isSelfPlaying) {
                    pbAudioProgress?.progress = 0
                    tvAudioDuration?.text = "0:00"
                }

                ivPlayPause?.let { btn ->
                    btn.setOnClickListener {
                        val pb = pbAudioProgress ?: return@setOnClickListener
                        val tv = tvAudioDuration ?: return@setOnClickListener
                        toggleAudioPlayback(message, btn, pb, tv)
                    }
                }

                if (message.text.isBlank() || message.text == "🎙️ Voice note" || message.text == "🎙️ Voice message") {
                    tvText.visibility = View.GONE
                } else {
                    tvText.text = message.text
                    tvText.visibility = View.VISIBLE
                }
            } else {
                layoutAudio?.visibility = View.GONE
                if (message.text.isNotBlank()) {
                    tvText.text = message.text
                    tvText.visibility = View.VISIBLE
                } else {
                    tvText.visibility = View.GONE
                }
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
                    .placeholder(R.drawable.placeholder_memory)
                    .error(R.drawable.placeholder_memory)
                    .centerCrop()
                    .into(ivImage)

                ivImage.setOnClickListener {
                    if (selectedMessageId != null) {
                        onMessageClick?.invoke(message)
                    } else {
                        onImageClick(message.imageUrl)
                    }
                }
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
        private val tvQuoteSender: TextView? = itemView.findViewById(R.id.tv_quote_sender)
        private val tvQuoteText: TextView? = itemView.findViewById(R.id.tv_quote_text)
        private val layoutAudio: View? = itemView.findViewById(R.id.layout_chat_audio)
        private val ivPlayPause: ImageView? = itemView.findViewById(R.id.iv_audio_play_pause)
        private val pbAudioProgress: ProgressBar? = itemView.findViewById(R.id.pb_audio_progress)
        private val tvAudioDuration: TextView? = itemView.findViewById(R.id.tv_audio_duration)

        fun bind(message: ChatMessage, partnerAvatarUrl: String?) {
            val isSelected = message.id == selectedMessageId
            rootLayout.setBackgroundResource(if (isSelected) R.drawable.bg_msg_selected else 0)

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

            // Quoted reply binding
            if (message.isReply) {
                layoutQuote?.visibility = View.VISIBLE
                tvQuoteSender?.text = message.replyToSenderName?.ifBlank { "Message" } ?: "Message"
                tvQuoteText?.text = message.replyToText ?: ""
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
            if (!message.audioUrl.isNullOrBlank()) {
                layoutAudio?.visibility = View.VISIBLE
                val isSelfPlaying = message.id == playingMessageId && mediaPlayer?.isPlaying == true
                ivPlayPause?.setImageResource(if (isSelfPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)

                if (!isSelfPlaying) {
                    pbAudioProgress?.progress = 0
                    tvAudioDuration?.text = "0:00"
                }

                ivPlayPause?.let { btn ->
                    btn.setOnClickListener {
                        val pb = pbAudioProgress ?: return@setOnClickListener
                        val tv = tvAudioDuration ?: return@setOnClickListener
                        toggleAudioPlayback(message, btn, pb, tv)
                    }
                }

                if (message.text.isBlank() || message.text == "🎙️ Voice note" || message.text == "🎙️ Voice message") {
                    tvText.visibility = View.GONE
                } else {
                    tvText.text = message.text
                    tvText.visibility = View.VISIBLE
                }
            } else {
                layoutAudio?.visibility = View.GONE
                if (message.text.isNotBlank()) {
                    tvText.text = message.text
                    tvText.visibility = View.VISIBLE
                } else {
                    tvText.visibility = View.GONE
                }
            }

            tvTime.text = timeFormat.format(Date(message.timestamp))

            if (!message.imageUrl.isNullOrBlank()) {
                cardImage.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(message.imageUrl)
                    .placeholder(R.drawable.placeholder_memory)
                    .error(R.drawable.placeholder_memory)
                    .centerCrop()
                    .into(ivImage)

                ivImage.setOnClickListener {
                    if (selectedMessageId != null) {
                        onMessageClick?.invoke(message)
                    } else {
                        onImageClick(message.imageUrl)
                    }
                }
            } else {
                cardImage.visibility = View.GONE
            }
        }
    }
}
