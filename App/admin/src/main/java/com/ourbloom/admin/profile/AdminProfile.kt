package com.ourbloom.admin.profile

data class AdminProfile(
    val adminName: String = "Narayan Phukan",
    val mobileNumber: String = "8822361549",
    val email: String = "narayan@ourbloom.app",
    val role: String = "Super Administrator",
    val passwordHash: String = "Tanayan@admin",
    val isBiometricEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
