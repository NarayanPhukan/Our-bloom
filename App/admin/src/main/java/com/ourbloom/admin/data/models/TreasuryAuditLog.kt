package com.ourbloom.admin.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Immutable audit trail tracking manual changes to PNB treasury positions.
 */
@IgnoreExtraProperties
data class TreasuryAuditLog(
    @DocumentId val id: String = "",
    val adminId: String = "",
    val adminName: String = "",
    val timestamp: Long = 0L,
    val bankName: String = "Punjab National Bank (PNB)",
    val fdReferenceNumber: String = "",
    val previousFdPrincipalPaise: Long = 0L,
    val newFdPrincipalPaise: Long = 0L,
    val previousLiquidReservePaise: Long = 0L,
    val newLiquidReservePaise: Long = 0L,
    val reason: String = "",
    val notes: String = "",
    // Document audit trail
    val documentAction: String = "UNCHANGED", // "ATTACHED", "REPLACED", "UNCHANGED", "REMOVED"
    val previousDocumentFileName: String = "",
    val newDocumentFileName: String = "",
    val documentUrl: String = ""
)
