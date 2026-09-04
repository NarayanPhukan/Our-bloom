package com.ourbloom.app.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Couple(
    @DocumentId val id: String = "",
    val user1: String = "",
    val user2: String = "",
    val startDate: String = "",
    val startTime: String = "00:00",
    val joinCode: String = "",
    val spotifyTrackId: String = "4O2N861eOnF9q8EtpH8IJu",
    val heroImageUrl: String = "",
    val slug: String = "",
    val chatBackgroundUrl: String = ""
)
