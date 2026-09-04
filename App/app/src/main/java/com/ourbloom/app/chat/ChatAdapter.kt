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

    fun submitList(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

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
        private val tvText: TextView = itemView.findViewById(R.id.tv_chat_text)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_chat_time)
        private val ivStatus: ImageView = itemView.findViewById(R.id.iv_chat_status)
        private val cardImage: View = itemView.findViewById(R.id.card_chat_image)
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_chat_image)

        fun bind(message: ChatMessage) {
            if (message.text.isNotBlank()) {
                tvText.text = message.text
                tvText.visibility = View.VISIBLE
            } else {
                tvText.visibility = View.GONE
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
                    ivStatus.contentDescription = "Read"
                }
                message.hasDelivered -> {
                    ivStatus.setImageResource(R.drawable.ic_msg_status_double_tick)
                    ImageViewCompat.setImageTintList(ivStatus, ColorStateList.valueOf(COLOR_TICK_DEFAULT))
                    ivStatus.contentDescription = "Delivered"
                }
                else -> {
                    ivStatus.setImageResource(R.drawable.ic_msg_status_single_tick)
                    ImageViewCompat.setImageTintList(ivStatus, ColorStateList.valueOf(COLOR_TICK_DEFAULT))
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
                    onImageClick(message.imageUrl)
                }
            } else {
                cardImage.visibility = View.GONE
            }
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSender: TextView = itemView.findViewById(R.id.tv_chat_sender)
        private val tvText: TextView = itemView.findViewById(R.id.tv_chat_text)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_chat_time)
        private val cardImage: View = itemView.findViewById(R.id.card_chat_image)
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_chat_image)
        private val ivPartnerAvatar: ImageView = itemView.findViewById(R.id.iv_chat_partner_avatar)

        fun bind(message: ChatMessage, partnerAvatarUrl: String?) {
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

            if (message.text.isNotBlank()) {
                tvText.text = message.text
                tvText.visibility = View.VISIBLE
            } else {
                tvText.visibility = View.GONE
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
                    onImageClick(message.imageUrl)
                }
            } else {
                cardImage.visibility = View.GONE
            }
        }
    }
}
