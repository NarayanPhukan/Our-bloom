package com.ourbloom.app.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class StoryEntry(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val type: String = "photo", // photo, message, voice_note, love_note, milestone, thumbkiss
    val title: String = "",
    val caption: String = "",
    val mediaUrl: String? = null,
    val mediaType: String? = null, // image, audio, video
    val messageId: String? = null,
    val senderId: String? = null,
    val senderName: String? = null,
    val content: String? = null,
    val location: String? = null,
    val visibility: String = "both",
    val createdAt: Long = System.currentTimeMillis()
)
