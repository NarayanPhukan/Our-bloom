package com.ourbloom.admin.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.ourbloom.admin.R
import com.ourbloom.admin.main.AdminMainActivity
import java.util.concurrent.Executor

class AdminAuthActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "bloom_admin_prefs"
        private const val KEY_IS_LOGGED_IN = "is_admin_logged_in"
        private const val KEY_ADMIN_MOBILE = "admin_mobile"

        const val ADMIN_MOBILE = "8822361549"
        const val ADMIN_PASSWORD_PRIMARY = "Tanayan@admin"
        const val ADMIN_PASSWORD_FALLBACK = "Narayan@admin"
    }

    private lateinit var etMobile: EditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnBiometric: MaterialButton
    private lateinit var tvError: TextView

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_auth)

        initViews()
        setupBiometric()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

        // If previously authenticated on this device, prompt biometrics for convenience
        if (wasLoggedIn && isBiometricAvailable()) {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun initViews() {
        etMobile = findViewById(R.id.et_admin_mobile)
        etPassword = findViewById(R.id.et_admin_password)
        btnLogin = findViewById(R.id.btn_admin_login)
        btnBiometric = findViewById(R.id.btn_biometric)
        tvError = findViewById(R.id.tv_auth_error)

        // Set active admin phone if empty
        val currentProfile = com.ourbloom.admin.profile.AdminProfileRepository.getProfile()
        if (etMobile.text.isNullOrBlank()) {
            etMobile.setText(currentProfile.mobileNumber.ifBlank { ADMIN_MOBILE })
        }

        btnLogin.setOnClickListener {
            performLogin()
        }

        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                performLogin()
                true
            } else {
                false
            }
        }

        btnBiometric.setOnClickListener {
            if (isBiometricAvailable()) {
                biometricPrompt.authenticate(promptInfo)
            } else {
                Toast.makeText(this, "Biometrics not enrolled on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performLogin() {
        val rawMobile = etMobile.text.toString().trim()
        val digits = rawMobile.filter { it.isDigit() }
        val mobile10 = if (digits.length >= 10) digits.takeLast(10) else digits
        val password = etPassword.text.toString()

        if (mobile10.isBlank() || password.isBlank()) {
            showError("Please enter both mobile number and password.")
            return
        }

        val isValid = com.ourbloom.admin.profile.AdminProfileRepository.validateCredentials(mobile10, password)

        if (isValid) {
            val profile = com.ourbloom.admin.profile.AdminProfileRepository.getProfile()
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_ADMIN_MOBILE, mobile10)
                .apply()

            unlockApp("Welcome ${profile.adminName} • Admin Access Granted 🌸")
        } else {
            showError("Invalid administrator credentials. Please check your mobile or password.")
        }
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun setupBiometric() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                unlockApp("Biometric Verified • Welcome 🌸")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    showError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                showError("Biometric not recognized. Please use your password.")
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.auth_biometric_title))
            .setSubtitle(getString(R.string.auth_biometric_subtitle))
            .setNegativeButtonText("Use Password")
            .build()
    }

    private fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun unlockApp(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        val intent = Intent(this, AdminMainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
