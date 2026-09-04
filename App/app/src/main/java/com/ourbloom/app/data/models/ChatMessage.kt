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
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    @get:PropertyName("isRead") @set:PropertyName("isRead") var isRead: Boolean = false,
    @get:PropertyName("isDelivered") @set:PropertyName("isDelivered") var isDelivered: Boolean = false,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSenderName: String? = null,
    val deletedFor: List<String> = emptyList()
) {
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
