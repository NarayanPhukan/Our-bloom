package com.ourbloom.app.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class WithdrawalRequest(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val requestedByUid: String = "",
    val requestedByName: String = "",
    val amount: Double = 0.0,
    val reason: String = "",
    val payoutMode: String = "JOINT", // "JOINT" or "SEPARATED"
    val jointAccount: BankAccountDetails? = null,
    val partner1Account: BankAccountDetails? = null,
    val partner1ShareAmount: Double = 0.0,
    val partner2Account: BankAccountDetails? = null,
    val partner2ShareAmount: Double = 0.0,
    val isEmergency: Boolean = false,
    val status: String = "PENDING_APPROVAL", // PENDING_APPROVAL, WAITING_PERIOD, PROCESSING_PAYOUT, COMPLETED, REJECTED, CANCELLED
    val requestedAt: Long = 0L,
    val partnerApprovedAt: Long? = null,
    val waitingPeriodEndsAt: Long? = null,
    val payoutExpectedBy: Long? = null,
    val completedAt: Long? = null
)
