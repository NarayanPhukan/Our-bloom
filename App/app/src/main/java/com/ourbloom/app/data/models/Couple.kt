package com.ourbloom.app.data.models

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class Couple(
    var id: String = "",
    var user1: String = "",
    var user2: String = "",
    var startDate: String = "",
    var startTime: String = "00:00",
    @get:PropertyName("inviteCode") @set:PropertyName("inviteCode") var joinCode: String = "",
    var spotifyTrackId: String = "4O2N861eOnF9q8EtpH8IJu",
    var heroImageUrl: String = "",
    var slug: String = "",
    var chatBackgroundUrl: String = "",
    var specialPhrase: String = ""
)
