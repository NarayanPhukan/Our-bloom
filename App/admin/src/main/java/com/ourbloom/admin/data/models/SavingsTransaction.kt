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
    val merchantUtr: String? = null,
    val settledAt: Long? = null,
    val settlementDate: String? = null,
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
) {
    val isSettled: Boolean
        get() = settlementStatus.equals("SETTLED", ignoreCase = true)

    val effectiveVaultPaise: Long
        get() = if (vaultAmountPaise > 0L) {
            vaultAmountPaise
        } else if (grossAmountPaise > 0L) {
            grossAmountPaise
        } else {
            Math.round(amount * 100.0)
        }

    val effectivePlatformFeePaise: Long
        get() = if (platformFeePaise > 0L) {
            platformFeePaise
        } else {
            // Standard 2% platform fee calculation: round-half-up integer division
            (effectiveVaultPaise * 200L + 5000L) / 10000L
        }

    val effectiveSettledPaise: Long
        get() = if (gatewaySettlementAmountPaise != null && gatewaySettlementAmountPaise > 0L) {
            gatewaySettlementAmountPaise
        } else {
            effectiveVaultPaise
        }

    val isGiftRevenue: Boolean
        get() = category.contains("gift", ignoreCase = true) || type.contains("gift", ignoreCase = true)

    val isSubscriptionRevenue: Boolean
        get() = category.contains("subscription", ignoreCase = true) || type.contains("subscription", ignoreCase = true)

    val settledAmount: Double
        get() = effectiveSettledPaise / 100.0
}
