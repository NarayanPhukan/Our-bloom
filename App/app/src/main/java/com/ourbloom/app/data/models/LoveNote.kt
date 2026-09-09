package com.ourbloom.app.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class LoveNote(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val content: String = "",
    val author: String = "",
    val isDailyAi: Boolean = false,
    val dateStr: String = "",
    val imageUrl: String = "",
    val audioUrl: String = "",
    val audioDuration: Int = 0,
    val createdAt: String? = null,
    val isScratchSecret: Boolean = false,
    val isRevealed: Boolean = false
)
