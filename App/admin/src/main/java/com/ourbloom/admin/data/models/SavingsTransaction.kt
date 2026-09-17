package com.ourbloom.admin.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class SavingsTransaction(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val userId: String = "",
    val userName: String = "",
    val type: String = "deposit", // "deposit" | "withdrawal" | "DEPOSIT_REVERSAL"
    val grossAmountPaise: Long = 0L,
    val platformFeePaise: Long = 0L,
    val netVaultCreditPaise: Long = 0L,
    val vaultAmountPaise: Long = 0L,
    val payableAmountPaise: Long = 0L,
    val pricingModel: String = "",
    val platformFeeBps: Long = 200L,
    val gatewayFeePaise: Long? = null,
    val gatewaySettlementAmountPaise: Long? = null,
    val settlementStatus: String = "PENDING",
    val providerReference: String? = null,
    val providerBankReference: String? = null,
    val platformTransactionId: String = "",
    val originalTransactionId: String? = null,
    val refundId: String? = null,
    val utrNumber: String = "",
    val goalId: String = "",
    val goalTitle: String = "",
    val note: String = "",
    val category: String = "Savings",
    val paymentMethod: String = "Online",
    val timestamp: Long = 0L,
    // Deprecated compatibility fields - non-authoritative display only
    @Deprecated("Use grossAmountPaise or formatRupees") val amount: Double = 0.0,
    @Deprecated("Use grossAmountPaise") val grossAmount: Double = 0.0,
    @Deprecated("Use platformFeePaise") val platformFee: Double = 0.0,
    @Deprecated("Use netVaultCreditPaise") val netVaultCredit: Double = 0.0
)
