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

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val note = getItem(position)
        if (holder is ImageNoteViewHolder) {
            holder.bind(note)
        } else if (holder is TextNoteViewHolder) {
            holder.bind(note)
        }
    }

    class TextNoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val tvAuthor: TextView = itemView.findViewById(R.id.tv_author)

        fun bind(note: LoveNote) {
            tvDate.text = formatDate(note.createdAt ?: note.dateStr)
            
            // Basic HTML strip since content might contain basic HTML from ReactQuill
            val cleanContent = note.content.replace(Regex("<.*?>"), "").replace("&nbsp;", " ")
            tvContent.text = cleanContent
            
            tvAuthor.text = "— ${note.author}"
        }
    }

    class ImageNoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_image)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)

        fun bind(note: LoveNote) {
            tvDate.text = formatDate(note.createdAt ?: note.dateStr)
            
            if (note.content.isNotBlank()) {
                val cleanContent = note.content.replace(Regex("<.*?>"), "").replace("&nbsp;", " ")
                tvContent.text = cleanContent
                tvContent.visibility = View.VISIBLE
            } else {
                tvContent.visibility = View.GONE
            }

            if (note.imageUrl.startsWith("/uploads")) {
                // If it's a relative URL from MongoDB, construct a fallback full URL
                val baseUrl = "http://10.0.2.2:5000" // Emulator localhost fallback
                Glide.with(itemView.context).load(baseUrl + note.imageUrl).into(ivImage)
            } else {
                Glide.with(itemView.context).load(note.imageUrl).into(ivImage)
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
