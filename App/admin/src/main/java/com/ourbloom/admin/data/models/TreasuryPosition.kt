package com.ourbloom.admin.data.models

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Tracks the physical location of backing funds (Active PNB FD Position + Bank Reserves).
 * All monetary amounts in pure integer Long paise.
 * fdInterestRateBps defaults to 0L and is required upon entry.
 */
@IgnoreExtraProperties
data class TreasuryPosition(
    val bankName: String = "Punjab National Bank (PNB)",
    val fdReferenceNumber: String = "",
    val fdPrincipalPaise: Long = 0L,
    val fdInterestRateBps: Long = 0L, // Required upon entry (e.g., 725L for 7.25%, no arbitrary hardcoded default)
    val fdStartDate: Long = 0L,
    val fdMaturityDate: Long = 0L,
    val fdMaturityValuePaise: Long = 0L,
    val fdAccruedInterestPaise: Long = 0L, // OurBloom interest income (NOT customer principal)
    val liquidBankReservePaise: Long = 0L, // Liquid checking account balance
    val lastUpdated: Long = 0L,
    val updatedBy: String = "system",
    // PNB e-FD Confirmation PDF Document Metadata
    val documentUrl: String = "",
    val documentFileName: String = "",
    val documentStoragePath: String = "",
    val documentUploadedBy: String = "",
    val documentUploadedAt: Long = 0L,
    val documentSizeBytes: Long = 0L
)
