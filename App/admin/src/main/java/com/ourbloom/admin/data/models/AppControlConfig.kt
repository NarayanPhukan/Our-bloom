package com.ourbloom.admin.data.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class AppControlConfig(
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "Our Bloom is currently undergoing scheduled maintenance. Please check back shortly 🌸",
    val minSupportedVersion: Int = 1,
    val forceUpdateUrl: String = "",
    val depositsEnabled: Boolean = true,
    val withdrawalsEnabled: Boolean = true,
    val videoCallsEnabled: Boolean = true,
    val loveNotesEnabled: Boolean = true,
    val updatedBy: String = "admin",
    val lastUpdated: Long = 0L
)
