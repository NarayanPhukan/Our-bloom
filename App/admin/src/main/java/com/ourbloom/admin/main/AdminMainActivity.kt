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
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.messaging.FirebaseMessaging
import com.ourbloom.admin.R
import com.ourbloom.admin.auth.AdminAuthActivity
import com.ourbloom.admin.dashboard.AdminDashboardFragment
import com.ourbloom.admin.fcm.AdminFirebaseMessagingService
import com.ourbloom.admin.payouts.AdminPayoutsFragment
import com.ourbloom.admin.profile.AdminProfileFragment
import com.ourbloom.admin.profile.AdminProfileRepository
import com.ourbloom.admin.transactions.AdminTransactionsFragment
import com.ourbloom.admin.wallets.AdminWalletsFragment
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.admin.broadcast.BroadcastDialog
import com.ourbloom.admin.config.AppControlDialog
import com.ourbloom.admin.data.AdminFirestoreRepository
import com.ourbloom.admin.data.models.SavingsTransaction
import com.ourbloom.admin.data.models.SavingsWallet
import com.ourbloom.admin.telemetry.SystemHealthDialog
import com.ourbloom.admin.treasury.TreasuryDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AdminMainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navDrawer: NavigationView
    private lateinit var bottomNav: BottomNavigationView

    private val repository = AdminFirestoreRepository()
    private var txListener: ListenerRegistration? = null
    private var walletListener: ListenerRegistration? = null
    private var cachedTransactions: List<SavingsTransaction> = emptyList()
    private var cachedWallets: List<SavingsWallet> = emptyList()

    private val dashboardFragment = AdminDashboardFragment()
    private val payoutsFragment = AdminPayoutsFragment()
    private val ledgerFragment = AdminTransactionsFragment()
    private val walletsFragment = AdminWalletsFragment()
    private val profileFragment = AdminProfileFragment()

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

        drawerLayout = findViewById(R.id.admin_drawer_layout)
        navDrawer = findViewById(R.id.admin_nav_drawer)
        bottomNav = findViewById(R.id.admin_bottom_nav)

        // Hamburger Menu Toggle
        findViewById<ImageButton>(R.id.btn_admin_menu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Profile Avatar Shortcut (Top Bar)
        findViewById<ImageButton>(R.id.btn_admin_profile_top).setOnClickListener {
            openProfileSection()
        }

        // Lock Button
        findViewById<ImageButton>(R.id.btn_admin_lock).setOnClickListener {
            lockAdmin()
        }

        // Bug Radar inspector dialog
        val btnBugRadar = findViewById<ImageButton>(R.id.btn_admin_bug_radar)
        btnBugRadar.setOnClickListener {
            com.ourbloom.admin.bugs.BugRadarDialog(this).show()
        }

        // Setup Drawer Menu & Header
        setupNavigationDrawer()

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

        // Handle system back navigation to close drawer if open
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Default screen is dashboard
        if (savedInstanceState == null) {
            switchFragment(dashboardFragment)
        }

        txListener = repository.observeTransactions { cachedTransactions = it }
        walletListener = repository.observeSavingsWallets { cachedWallets = it }

        setupFinancialNotifications()
    }

    private fun setupNavigationDrawer() {
        val headerView = navDrawer.getHeaderView(0)
        val tvHeaderInitial = headerView.findViewById<TextView>(R.id.tv_nav_avatar_initial)
        val tvHeaderName = headerView.findViewById<TextView>(R.id.tv_nav_admin_name)
        val tvHeaderPhone = headerView.findViewById<TextView>(R.id.tv_nav_admin_phone)

        // Live observe admin profile to keep drawer header up to date
        lifecycleScope.launch {
            AdminProfileRepository.profileState.collectLatest { profile ->
                tvHeaderName.text = profile.adminName
                tvHeaderPhone.text = "+91 ${profile.mobileNumber}"
                tvHeaderInitial.text = if (profile.adminName.isNotBlank()) profile.adminName.first().uppercase() else "A"
            }
        }

        headerView.setOnClickListener {
            openProfileSection()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        navDrawer.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.drawer_item_dashboard -> {
                    switchFragment(dashboardFragment)
                    bottomNav.selectedItemId = R.id.nav_item_dashboard
                }
                R.id.drawer_item_payouts -> {
                    switchFragment(payoutsFragment)
                    bottomNav.selectedItemId = R.id.nav_item_payouts
                }
                R.id.drawer_item_ledger -> {
                    switchFragment(ledgerFragment)
                    bottomNav.selectedItemId = R.id.nav_item_ledger
                }
                R.id.drawer_item_wallets -> {
                    switchFragment(walletsFragment)
                    bottomNav.selectedItemId = R.id.nav_item_wallets
                }
                R.id.drawer_item_treasury -> {
                    TreasuryDialog(this, cachedTransactions, cachedWallets, repository, lifecycleScope).show()
                }
                R.id.drawer_item_health -> {
                    SystemHealthDialog(this, repository, lifecycleScope).show()
                }
                R.id.drawer_item_broadcast -> {
                    BroadcastDialog(this, null, repository, lifecycleScope).show()
                }
                R.id.drawer_item_app_control -> {
                    AppControlDialog(this, repository, lifecycleScope).show()
                }
                R.id.drawer_item_profile -> {
                    openProfileSection()
                }
                R.id.drawer_item_radar -> {
                    com.ourbloom.admin.bugs.BugRadarDialog(this).show()
                }
                R.id.drawer_item_updates -> {
                    com.ourbloom.admin.updates.AdminUpdateManager.checkForUpdates(this, manualCheck = true)
                }
                R.id.drawer_item_lock -> {
                    lockAdmin()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun openProfileSection() {
        switchFragment(profileFragment)
    }

    private fun lockAdmin() {
        val intent = Intent(this, AdminAuthActivity::class.java)
        startActivity(intent)
        finish()
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

    override fun onResume() {
        super.onResume()
        com.ourbloom.admin.updates.AdminUpdateManager.onResume(this)
    }

    fun navigateToTab(tabId: Int) {
        bottomNav.selectedItemId = tabId
    }

    override fun onDestroy() {
        super.onDestroy()
        txListener?.remove()
        walletListener?.remove()
    }
}
