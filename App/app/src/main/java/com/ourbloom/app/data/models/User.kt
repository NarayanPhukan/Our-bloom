package com.ourbloom.app.data.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    var uid: String = "",
    var name: String = "",
    var email: String = "",
    var coupleId: String = "",
    var isPremium: Boolean = false,
    var avatarUrl: String = "",
    var nicknameForPartner: String = "",
    var connectedGoogleEmail: String = "",
    var fcmToken: String = ""
)
