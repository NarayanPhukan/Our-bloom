package com.ourbloom.admin.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Authoritative canonical ledger for OurBloom earned revenue.
 * Strictly adheres to:
 * 1. One financial event -> Exactly one immutable RevenueRecord.
 * 2. Pure integer paise (Long). Zero Double floating point in data models.
 * 3. netRevenuePaise = grossAmountPaise - (sellerPayablePaise + gatewayFeePaise + taxPaise).
 */
@IgnoreExtraProperties
data class RevenueRecord(
    @DocumentId val id: String = "",
    val eventId: String = "", // Standardized deterministic namespace: REV_SAVINGS_TXN_<txnId> | REV_GIFT_<orderId> | REV_SUB_<subId> | REV_MANUAL_<uuid>
    val source: String = "SAVINGS_TRANSACTION", // "SAVINGS_TRANSACTION" | "GIFT_STORE" | "SUBSCRIPTION_SYSTEM" | "MANUAL_ENTRY"
    val sourceTransactionId: String = "",
    val type: String = "FEE", // "FEE" | "GIFT" | "SUBSCRIPTION" | "ADJUSTMENT"
    val title: String = "",
    val grossAmountPaise: Long = 0L,
    val sellerPayablePaise: Long = 0L, // Cost of goods owed to external vendors (gifts)
    val gatewayFeePaise: Long = 0L,    // MDR fee deducted by PayU
    val taxPaise: Long = 0L,           // GST / tax obligations
    val netRevenuePaise: Long = 0L,    // OurBloom's legitimate earned revenue
    val coupleId: String = "",
    val userId: String = "",
    val userName: String = "",
    val paymentMethod: String = "PayU",
    val referenceId: String = "",
    val manualEntryReason: String? = null,
    val createdBy: String? = null,
    val metadata: Map<String, Any>? = null,
    val timestamp: Long = 0L
)
