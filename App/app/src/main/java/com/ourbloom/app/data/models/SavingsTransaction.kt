package com.ourbloom.app.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class SavingsTransaction(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val userId: String = "",
    val userName: String = "",
    val type: String = "deposit", // "deposit" | "withdrawal"
    val amount: Double = 0.0,
    val utrNumber: String = "",
    val goalId: String = "",
    val goalTitle: String = "",
    val note: String = "",
    val category: String = "Savings",
    val paymentMethod: String = "UPI",
    val timestamp: Long = 0L
)
