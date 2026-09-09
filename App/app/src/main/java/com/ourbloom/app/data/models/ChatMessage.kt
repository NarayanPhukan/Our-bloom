package com.ourbloom.app.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    @get:PropertyName("imageUrl") @set:PropertyName("imageUrl") var imageUrl: String? = null,
    @get:PropertyName("audioUrl") @set:PropertyName("audioUrl") var audioUrl: String? = null,
    @get:PropertyName("audioDurationMs") @set:PropertyName("audioDurationMs") var audioDurationMs: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    @get:PropertyName("isRead") @set:PropertyName("isRead") var isRead: Boolean = false,
    @get:PropertyName("isDelivered") @set:PropertyName("isDelivered") var isDelivered: Boolean = false,
    @get:PropertyName("isPending") @set:PropertyName("isPending") var isPending: Boolean = false,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSenderName: String? = null,
    @get:PropertyName("replyToImageUrl") @set:PropertyName("replyToImageUrl") var replyToImageUrl: String? = null,
    val deletedFor: List<String> = emptyList(),
    val readAt: Long? = null,
    val deliveredAt: Long? = null,
    @get:PropertyName("isSticker") @set:PropertyName("isSticker") var isSticker: Boolean = false,
    val reactions: Map<String, String> = emptyMap()
) {
    val isStickerMessage: Boolean
        get() = isSticker || (!imageUrl.isNullOrBlank() && text == "[Sticker]")
    val isSeen: Boolean
        get() = isRead

    val hasDelivered: Boolean
        get() = isDelivered || isRead

    val isReply: Boolean
        get() = !replyToText.isNullOrBlank()

    @PropertyName("read")
    fun setReadField(value: Boolean) {
        this.isRead = this.isRead || value
    }

    @PropertyName("delivered")
    fun setDeliveredField(value: Boolean) {
        this.isDelivered = this.isDelivered || value
    }
}
