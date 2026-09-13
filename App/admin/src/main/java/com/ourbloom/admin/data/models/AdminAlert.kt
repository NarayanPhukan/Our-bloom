package com.ourbloom.admin.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class AdminAlert(
    @DocumentId val id: String = "",
    val type: String = "", // "deposit" | "payout_needed"
    val coupleId: String = "",
    val userId: String = "",
    val userName: String = "",
    val amount: Double = 0.0,
    val utrNumber: String = "",
    val note: String = "",
    val timestamp: Long = 0L,
    val status: String = "", // "VERIFIED" | "PENDING_DISBURSEMENT" | "DISBURSED" | "REJECTED"
    val dueWithinHours: Int = 48,
    val payoutMode: String = "",
    val reason: String = "",
    val jointAccount: BankAccountDetails? = null
)
