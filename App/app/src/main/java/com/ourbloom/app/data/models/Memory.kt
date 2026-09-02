package com.ourbloom.app.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Memory(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val imageUrl: String = "",
    val title: String = "",
    val dateStr: String = "",
    val isFavorite: Boolean = false,
    val audioUrl: String = "",
    val authorId: String = "",
    val createdAt: String? = null
)
