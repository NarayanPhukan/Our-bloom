package com.ourbloom.app.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class SavingsGoal(
    val id: String = "",
    val coupleId: String = "",
    val title: String = "",
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val icon: String = "savings",
    val category: String = "General",
    val targetDate: String = "",
    val createdAt: Long = 0L,
    val isCompleted: Boolean = false
) {
    val isGoalCompleted: Boolean
        get() = isCompleted || (targetAmount > 0 && currentAmount >= targetAmount)
}
