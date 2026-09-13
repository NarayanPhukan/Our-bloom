package com.ourbloom.admin.main

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.messaging.FirebaseMessaging
import com.ourbloom.admin.R
import com.ourbloom.admin.auth.AdminAuthActivity
import com.ourbloom.admin.dashboard.AdminDashboardFragment
import com.ourbloom.admin.fcm.AdminFirebaseMessagingService
import com.ourbloom.admin.payouts.AdminPayoutsFragment
import com.ourbloom.admin.transactions.AdminTransactionsFragment
import com.ourbloom.admin.wallets.AdminWalletsFragment

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AdminMainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    private val dashboardFragment = AdminDashboardFragment()
    private val payoutsFragment = AdminPayoutsFragment()
    private val ledgerFragment = AdminTransactionsFragment()
    private val walletsFragment = AdminWalletsFragment()

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("AdminMainActivity", "POST_NOTIFICATIONS granted")
        } else {
            Log.w("AdminMainActivity", "POST_NOTIFICATIONS declined")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_main)

        bottomNav = findViewById(R.id.admin_bottom_nav)

        findViewById<ImageButton>(R.id.btn_admin_lock).setOnClickListener {
            // Lock and return to auth screen
            val intent = Intent(this, AdminAuthActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Bug Radar inspector dialog
        val btnBugRadar = findViewById<ImageButton>(R.id.btn_admin_bug_radar)
        btnBugRadar.setOnClickListener {
            com.ourbloom.admin.bugs.BugRadarDialog(this).show()
        }

        // Auto-update checker (manual trigger)
        findViewById<ImageButton>(R.id.btn_admin_update).setOnClickListener {
            com.ourbloom.admin.updates.AdminUpdateManager.checkForUpdates(this, manualCheck = true)
        }

        // Live Bug Radar monitoring & snackbar alert on new bugs
        com.ourbloom.admin.bugs.AdminBugRadar.bindToActivity(this)

        // Observe bug state to tint Bug Radar icon
        lifecycleScope.launch {
            com.ourbloom.admin.bugs.AdminBugRadar.bugsState.collect { bugs ->
                val hasCritical = bugs.any { it.severity == com.ourbloom.admin.bugs.BugSeverity.CRITICAL }
                val colorRes = when {
                    hasCritical -> R.color.admin_crimson
                    bugs.isNotEmpty() -> R.color.admin_amber
                    else -> R.color.admin_emerald
                }
                btnBugRadar.setColorFilter(ContextCompat.getColor(this@AdminMainActivity, colorRes))
            }
        }

        // Silent auto-detect update check on startup
        com.ourbloom.admin.updates.AdminUpdateManager.checkForUpdates(this, manualCheck = false)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_item_dashboard -> {
                    switchFragment(dashboardFragment)
                    true
                }
                R.id.nav_item_payouts -> {
                    switchFragment(payoutsFragment)
                    true
                }
                R.id.nav_item_ledger -> {
                    switchFragment(ledgerFragment)
                    true
                }
                R.id.nav_item_wallets -> {
                    switchFragment(walletsFragment)
                    true
                }
                else -> false
            }
        }

        // Default screen is dashboard
        if (savedInstanceState == null) {
            switchFragment(dashboardFragment)
        }

        setupFinancialNotifications()
    }

    private fun setupFinancialNotifications() {
        // 1. Create High Importance Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AdminFirebaseMessagingService.CHANNEL_ID,
                AdminFirebaseMessagingService.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts for deposits, withdrawal requests, and couple vault activity"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        // 2. Request runtime permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 3. Subscribe to topic & register device token
        AdminFirebaseMessagingService.ensureTopicSubscription()
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            AdminFirebaseMessagingService.registerDeviceToken(token)
        }
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.admin_fragment_container, fragment)
            .commit()
    }

    fun navigateToTab(tabId: Int) {
        bottomNav.selectedItemId = tabId
    }
}
