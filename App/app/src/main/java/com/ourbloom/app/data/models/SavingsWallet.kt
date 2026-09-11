package com.ourbloom.app.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class SavingsWallet(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val totalBalance: Double = 0.0,
    val currency: String = "₹",
    val user1Id: String = "",
    val user1Name: String = "",
    val user1Total: Double = 0.0,
    val user2Id: String = "",
    val user2Name: String = "",
    val user2Total: Double = 0.0,
    val lockUntilDate: Long = 0L,
    val lastUpdated: Long = 0L
)
