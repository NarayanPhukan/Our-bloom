package com.ourbloom.app.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class DreamLocation(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val title: String = "",
    val description: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val status: String = "Dreaming", // Dreaming, Planning, Booked, Visited
    val photoUrl: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null
)
