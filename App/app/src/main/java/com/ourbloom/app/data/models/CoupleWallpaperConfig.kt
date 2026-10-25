package com.ourbloom.app.data.models

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class CoupleWallpaperConfig(
    val imageUrl: String = "",
    val showDaysCounter: Boolean = true,
    val showCoupleNames: Boolean = true,
    val showMilestone: Boolean = true,
    val dimLevel: Float = 0.25f,
    val presetTheme: String = "CUSTOM", // "CUSTOM", "ROSE_SUNSET", "STARRY_LOVE", "CHERRY_BLOSSOM", "GOLDEN_HOUR"
    val templateId: String = "CLASSIC", // "CLASSIC", "HEART_MEMORIES"
    val memoryPhotoUrls: List<String> = emptyList(),
    val badgePosition: String = "BOTTOM", // "TOP", "CENTER", "BOTTOM"
    val quoteText: String = "Forever & Always 💕",
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
