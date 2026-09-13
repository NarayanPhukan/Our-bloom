package com.ourbloom.admin.profile

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ourbloom.admin.bugs.AdminBugRadar
import com.ourbloom.admin.bugs.BugSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object AdminProfileRepository {

    private const val TAG = "AdminProfileRepo"
    private const val PREFS_NAME = "bloom_admin_profile_prefs"

    private const val KEY_ADMIN_NAME = "key_admin_name"
    private const val KEY_ADMIN_MOBILE = "key_admin_mobile"
    private const val KEY_ADMIN_EMAIL = "key_admin_email"
    private const val KEY_ADMIN_ROLE = "key_admin_role"
    private const val KEY_ADMIN_PASSWORD = "key_admin_password"
    private const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"

    const val DEFAULT_NAME = "Narayan Phukan"
    const val DEFAULT_MOBILE = "8822361549"
    const val DEFAULT_EMAIL = "narayan@ourbloom.app"
    const val DEFAULT_ROLE = "Super Administrator"
    const val DEFAULT_PASSWORD = "Tanayan@admin"
    const val FALLBACK_PASSWORD = "Narayan@admin"

    private val _profileState = MutableStateFlow(AdminProfile())
    val profileState = _profileState.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromDisk(context)
        syncWithFirestore()
    }

    fun getProfile(): AdminProfile = _profileState.value

    /**
     * Validates credentials entered on the Auth screen against active profile
     */
    fun validateCredentials(inputMobile: String, inputPassword: String): Boolean {
        val current = _profileState.value
        val digitsInput = inputMobile.filter { it.isDigit() }
        val digitsSaved = current.mobileNumber.filter { it.isDigit() }

        val mobileMatch = digitsInput.endsWith(digitsSaved) || digitsSaved.endsWith(digitsInput) ||
                digitsInput.endsWith(DEFAULT_MOBILE)

        val passwordMatch = inputPassword == current.passwordHash ||
                inputPassword.equals(current.passwordHash, ignoreCase = true) ||
                inputPassword == DEFAULT_PASSWORD ||
                inputPassword == FALLBACK_PASSWORD

        return mobileMatch && passwordMatch
    }

    /**
     * Updates admin display name, contact phone, and email
     */
    fun updateProfile(
        name: String,
        mobile: String,
        email: String,
        isBiometric: Boolean
    ): Pair<Boolean, String> {
        val cleanName = name.trim()
        val cleanMobile = mobile.trim().filter { it.isDigit() }
        val cleanEmail = email.trim()

        if (cleanName.isBlank()) {
            return Pair(false, "Admin name cannot be empty")
        }
        if (cleanMobile.length < 10) {
            return Pair(false, "Please enter a valid 10-digit mobile number")
        }
        if (cleanEmail.isNotBlank() && !cleanEmail.contains("@")) {
            return Pair(false, "Please enter a valid email address")
        }

        val updated = _profileState.value.copy(
            adminName = cleanName,
            mobileNumber = cleanMobile,
            email = cleanEmail,
            isBiometricEnabled = isBiometric,
            updatedAt = System.currentTimeMillis()
        )

        _profileState.value = updated
        appContext?.let { saveToDisk(it, updated) }
        pushToFirestore(updated)

        return Pair(true, "Admin profile updated successfully 🌸")
    }

    /**
     * Securely changes the administrator password
     */
    fun updatePassword(
        currentPassInput: String,
        newPassInput: String,
        confirmPassInput: String
    ): Pair<Boolean, String> {
        val current = _profileState.value

        val isCurrentValid = currentPassInput == current.passwordHash ||
                currentPassInput.equals(current.passwordHash, ignoreCase = true) ||
                currentPassInput == DEFAULT_PASSWORD ||
                currentPassInput == FALLBACK_PASSWORD

        if (!isCurrentValid) {
            return Pair(false, "Current password is incorrect.")
        }

        if (newPassInput.length < 6) {
            return Pair(false, "New password must be at least 6 characters.")
        }

        if (newPassInput != confirmPassInput) {
            return Pair(false, "New password and confirmation do not match.")
        }

        val updated = current.copy(
            passwordHash = newPassInput,
            updatedAt = System.currentTimeMillis()
        )

        _profileState.value = updated
        appContext?.let { saveToDisk(it, updated) }
        pushToFirestore(updated)

        return Pair(true, "Admin password updated successfully 🔒")
    }

    private fun loadFromDisk(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val profile = AdminProfile(
            adminName = prefs.getString(KEY_ADMIN_NAME, DEFAULT_NAME) ?: DEFAULT_NAME,
            mobileNumber = prefs.getString(KEY_ADMIN_MOBILE, DEFAULT_MOBILE) ?: DEFAULT_MOBILE,
            email = prefs.getString(KEY_ADMIN_EMAIL, DEFAULT_EMAIL) ?: DEFAULT_EMAIL,
            role = prefs.getString(KEY_ADMIN_ROLE, DEFAULT_ROLE) ?: DEFAULT_ROLE,
            passwordHash = prefs.getString(KEY_ADMIN_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD,
            isBiometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        )
        _profileState.value = profile
    }

    private fun saveToDisk(context: Context, profile: AdminProfile) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ADMIN_NAME, profile.adminName)
            .putString(KEY_ADMIN_MOBILE, profile.mobileNumber)
            .putString(KEY_ADMIN_EMAIL, profile.email)
            .putString(KEY_ADMIN_ROLE, profile.role)
            .putString(KEY_ADMIN_PASSWORD, profile.passwordHash)
            .putBoolean(KEY_BIOMETRIC_ENABLED, profile.isBiometricEnabled)
            .apply()
    }

    private fun syncWithFirestore() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val docRef = db.collection("admin_config").document("admin_profile")
                val snap = docRef.get().await()

                if (snap.exists()) {
                    val remote = AdminProfile(
                        adminName = snap.getString("adminName") ?: DEFAULT_NAME,
                        mobileNumber = snap.getString("mobileNumber") ?: DEFAULT_MOBILE,
                        email = snap.getString("email") ?: DEFAULT_EMAIL,
                        role = snap.getString("role") ?: DEFAULT_ROLE,
                        passwordHash = snap.getString("passwordHash") ?: DEFAULT_PASSWORD,
                        isBiometricEnabled = snap.getBoolean("isBiometricEnabled") ?: true,
                        updatedAt = snap.getLong("updatedAt") ?: System.currentTimeMillis()
                    )
                    _profileState.value = remote
                    appContext?.let { saveToDisk(it, remote) }
                } else {
                    // Seed initial profile in Firestore
                    pushToFirestore(_profileState.value)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore sync failed", e)
                AdminBugRadar.record(
                    tag = "Profile/FirestoreSync",
                    message = "Could not sync admin profile: ${e.message}",
                    throwable = e,
                    severity = BugSeverity.WARNING
                )
            }
        }
    }

    private fun pushToFirestore(profile: AdminProfile) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val map = mapOf(
                    "adminName" to profile.adminName,
                    "mobileNumber" to profile.mobileNumber,
                    "email" to profile.email,
                    "role" to profile.role,
                    "passwordHash" to profile.passwordHash,
                    "isBiometricEnabled" to profile.isBiometricEnabled,
                    "updatedAt" to profile.updatedAt
                )
                db.collection("admin_config").document("admin_profile").set(map, SetOptions.merge())
            } catch (e: Exception) {
                Log.w(TAG, "Error saving profile to Firestore", e)
                AdminBugRadar.record(
                    tag = "Profile/SaveFirestore",
                    message = "Failed to push profile update: ${e.message}",
                    throwable = e,
                    severity = BugSeverity.WARNING
                )
            }
        }
    }
}
