package com.ourbloom.app.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ourbloom.app.R
import com.ourbloom.app.data.models.LoveNote
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class LoveNotesAdapter : ListAdapter<LoveNote, RecyclerView.ViewHolder>(LoveNoteDiffCallback()) {

    companion object {
        private const val TYPE_TEXT = 1
        private const val TYPE_IMAGE = 2
    }

    override fun getItemViewType(position: Int): Int {
        val note = getItem(position)
        return if (note.imageUrl.isNotBlank()) TYPE_IMAGE else TYPE_TEXT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_IMAGE) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_love_note_image, parent, false)
            ImageNoteViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_love_note_text, parent, false)
            TextNoteViewHolder(view)
        }
    }

    var onNoteRevealed: ((LoveNote) -> Unit)? = null
    var onNoteLongClick: ((LoveNote) -> Unit)? = null
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var currentPlayingNoteId: String? = null
    private var currentPlayingButton: android.widget.ImageButton? = null

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val note = getItem(position)
        holder.itemView.setOnLongClickListener {
            onNoteLongClick?.invoke(note)
            true
        }
        if (holder is ImageNoteViewHolder) {
            holder.bind(note, onAudioClick = { btn -> toggleAudioPlayback(note, btn) }, onRevealed = { onNoteRevealed?.invoke(note) })
        } else if (holder is TextNoteViewHolder) {
            holder.bind(note, onAudioClick = { btn -> toggleAudioPlayback(note, btn) }, onRevealed = { onNoteRevealed?.invoke(note) })
        }
    }

    private fun toggleAudioPlayback(note: LoveNote, button: android.widget.ImageButton) {
        if (note.audioUrl.isBlank()) return

        if (currentPlayingNoteId == note.id && mediaPlayer != null) {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                button.setImageResource(R.drawable.ic_play_arrow)
            } else {
                mediaPlayer?.start()
                button.setImageResource(R.drawable.ic_pause)
            }
            return
        }

        stopAudioPlayback()

        try {
            val player = android.media.MediaPlayer().apply {
                setDataSource(note.audioUrl)
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    button.setImageResource(R.drawable.ic_pause)
                }
                setOnCompletionListener {
                    button.setImageResource(R.drawable.ic_play_arrow)
                    stopAudioPlayback()
                }
                setOnErrorListener { _, _, _ ->
                    button.setImageResource(R.drawable.ic_play_arrow)
                    stopAudioPlayback()
                    true
                }
            }
            mediaPlayer = player
            currentPlayingNoteId = note.id
            currentPlayingButton = button
        } catch (e: Exception) {
            button.setImageResource(R.drawable.ic_play_arrow)
        }
    }

    fun stopAudioPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        currentPlayingButton?.setImageResource(R.drawable.ic_play_arrow)
        currentPlayingButton = null
        currentPlayingNoteId = null
    }

    class TextNoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val tvAuthor: TextView = itemView.findViewById(R.id.tv_author)
        private val layoutAudio: View? = itemView.findViewById(R.id.layout_audio_playback)
        private val btnPlayAudio: android.widget.ImageButton? = itemView.findViewById(R.id.btn_play_audio)
        private val tvAudioDuration: TextView? = itemView.findViewById(R.id.tv_audio_duration)
        private val viewScratchOverlay: com.ourbloom.app.ui.ScratchCardView? = itemView.findViewById(R.id.view_scratch_overlay)

        fun bind(note: LoveNote, onAudioClick: (android.widget.ImageButton) -> Unit, onRevealed: () -> Unit) {
            tvDate.text = formatDate(note.createdAt ?: note.dateStr)
            
            // Basic HTML strip since content might contain basic HTML from ReactQuill
            val cleanContent = note.content.replace(Regex("<.*?>"), "").replace("&nbsp;", " ")
            tvContent.text = cleanContent
            tvAuthor.text = "— ${note.author}"

            if (!note.audioUrl.isNullOrBlank()) {
                layoutAudio?.visibility = View.VISIBLE
                val durSec = note.audioDuration.takeIf { it > 0 } ?: 0
                val mins = durSec / 60
                val secs = durSec % 60
                tvAudioDuration?.text = if (durSec > 0) String.format(Locale.US, "%d:%02d", mins, secs) else "Voice Note"
                btnPlayAudio?.setOnClickListener {
                    btnPlayAudio?.let { btn -> onAudioClick(btn) }
                }
            } else {
                layoutAudio?.visibility = View.GONE
            }

            if (note.isScratchSecret && !note.isRevealed && viewScratchOverlay != null) {
                viewScratchOverlay.reset()
                viewScratchOverlay.onScratchRevealed = onRevealed
            } else {
                viewScratchOverlay?.revealInstantly()
            }
        }
    }

    class ImageNoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_image)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        private val layoutAudio: View? = itemView.findViewById(R.id.layout_audio_playback)
        private val btnPlayAudio: android.widget.ImageButton? = itemView.findViewById(R.id.btn_play_audio)
        private val tvAudioDuration: TextView? = itemView.findViewById(R.id.tv_audio_duration)
        private val viewScratchOverlay: com.ourbloom.app.ui.ScratchCardView? = itemView.findViewById(R.id.view_scratch_overlay)

        fun bind(note: LoveNote, onAudioClick: (android.widget.ImageButton) -> Unit, onRevealed: () -> Unit) {
            tvDate.text = formatDate(note.createdAt ?: note.dateStr)
            
            if (note.content.isNotBlank()) {
                val cleanContent = note.content.replace(Regex("<.*?>"), "").replace("&nbsp;", " ")
                tvContent.text = cleanContent
                tvContent.visibility = View.VISIBLE
            } else {
                tvContent.visibility = View.GONE
            }

            if (note.imageUrl.startsWith("/uploads")) {
                val baseUrl = "http://10.0.2.2:5000"
                Glide.with(itemView.context).load(baseUrl + note.imageUrl).into(ivImage)
            } else {
                Glide.with(itemView.context).load(note.imageUrl).into(ivImage)
            }

            if (!note.audioUrl.isNullOrBlank()) {
                layoutAudio?.visibility = View.VISIBLE
                val durSec = note.audioDuration.takeIf { it > 0 } ?: 0
                val mins = durSec / 60
                val secs = durSec % 60
                tvAudioDuration?.text = if (durSec > 0) String.format(Locale.US, "%d:%02d", mins, secs) else "Voice Note"
                btnPlayAudio?.setOnClickListener {
                    btnPlayAudio?.let { btn -> onAudioClick(btn) }
                }
            } else {
                layoutAudio?.visibility = View.GONE
            }

            if (note.isScratchSecret && !note.isRevealed && viewScratchOverlay != null) {
                viewScratchOverlay.reset()
                viewScratchOverlay.onScratchRevealed = onRevealed
            } else {
                viewScratchOverlay?.revealInstantly()
            }
        }
    }
}

private fun formatDate(isoString: String): String {
    if (isoString.isBlank()) return ""
    return try {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = isoFormat.parse(isoString) ?: return isoString
        val outFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
        outFormat.format(date)
    } catch (e: Exception) {
        // Fallback if not ISO
        isoString
    }
}

class LoveNoteDiffCallback : DiffUtil.ItemCallback<LoveNote>() {
    override fun areItemsTheSame(oldItem: LoveNote, newItem: LoveNote): Boolean {
        return oldItem.id == newItem.id
    }
    override fun areContentsTheSame(oldItem: LoveNote, newItem: LoveNote): Boolean {
        return oldItem == newItem
    }
}
