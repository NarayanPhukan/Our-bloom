package com.ourbloom.app.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Milestone(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val day: Int = 0,
    val label: String = "",
    val title: String = "",
    val body: String = "",
    val imageUrl: String = "",
    val icon: String = "local_florist",
    val iconFill: Boolean = false,
    val colorScheme: String = "primary",
    val aspectRatio: String = "video"
)
