package com.ourbloom.app.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    @DocumentId val uid: String = "",
    val name: String = "",
    val email: String = "",
    val coupleId: String? = null,
    val isPremium: Boolean = false,
    val avatarUrl: String = "",
    val nicknameForPartner: String = ""
)
