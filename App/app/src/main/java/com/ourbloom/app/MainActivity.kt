package com.ourbloom.app

import android.os.Bundle
import android.util.Log
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.ourbloom.app.util.ErrorReporter
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.updates.AppUpdateHelper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ourbloom.app.fcm.MyFirebaseMessagingService
import com.ourbloom.app.workers.AppUpdateWorker
import com.ourbloom.app.workers.PingServerWorker
import com.ourbloom.app.workers.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var appUpdateHelper: AppUpdateHelper

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        // Handle permission results if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Error auto-detection and crash reporting
        ErrorReporter.bindToActivity(this)
        ErrorReporter.checkAndPromptPendingCrash(this)

        try {
            appUpdateHelper = AppUpdateHelper(this)
            appUpdateHelper.checkForUpdates()
        } catch (e: Throwable) {
            Log.e("MainActivity", "AppUpdateHelper error: ${e.message}")
        }
        
        requestPermissions.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).let { 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it + Manifest.permission.POST_NOTIFICATIONS
            } else {
                it
            }
        })
        
        setupNavigation()
        setupBackgroundWorkers()
        fetchAndSaveFcmToken()
        promptIgnoreBatteryOptimizationIfNeeded()

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                fetchAndSaveFcmToken()
            }
        }
    }

    private fun promptIgnoreBatteryOptimizationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                val pkgName = packageName
                if (pm != null && !pm.isIgnoringBatteryOptimizations(pkgName)) {
                    val prefs = getSharedPreferences("ourbloom_system_prefs", Context.MODE_PRIVATE)
                    val hasPrompted = prefs.getBoolean("battery_optimization_prompted", false)
                    if (!hasPrompted) {
                        prefs.edit().putBoolean("battery_optimization_prompted", true).apply()
                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:$pkgName")
                        }
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Battery optimization prompt error: ${e.message}")
            }
        }
    }
    
    private var isKeyboardOpen = false
    private var isNavExplicitlyHidden = false

    fun setBottomNavVisibility(visible: Boolean) {
        isNavExplicitlyHidden = !visible
        updateBottomNavState()
    }

    fun getAppUpdateHelper(): AppUpdateHelper = appUpdateHelper

    private fun updateBottomNavState() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation) ?: return
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val currentDestId = navHostFragment?.navController?.currentDestination?.id
        val isExcluded = currentDestId in listOf(
            R.id.loginFragment, R.id.registerFragment, R.id.setupCoupleFragment, R.id.chatFragment
        )
        if (isExcluded || isNavExplicitlyHidden || isKeyboardOpen) {
            bottomNav.visibility = View.GONE
        } else {
            bottomNav.visibility = View.VISIBLE
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Dynamically set start destination based on current authentication state
        // This ensures authenticated users go directly to DashboardFragment with ZERO flash of the Login screen!
        if (navController.currentDestination == null) {
            val navInflater = navController.navInflater
            val graph = navInflater.inflate(R.navigation.nav_graph)
            val currentUser = FirebaseAuth.getInstance().currentUser
            val startDest = if (currentUser != null) {
                R.id.dashboardFragment
            } else {
                R.id.loginFragment
            }
            graph.setStartDestination(startDest)
            navController.graph = graph
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)

        // Automatically hide floating bottom navigation bar whenever the soft keyboard opens
        // so it NEVER pushes up with the keyboard or causes typing misclicks
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val isNowOpen = imeVisible || (imeHeight > 100)
            if (isKeyboardOpen != isNowOpen) {
                isKeyboardOpen = isNowOpen
                updateBottomNavState()
            }
            insets
        }

        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - r.bottom
            val isNowOpen = keypadHeight > screenHeight * 0.15
            if (isKeyboardOpen != isNowOpen) {
                isKeyboardOpen = isNowOpen
                updateBottomNavState()
            }
        }
        
        // Hide bottom navigation on auth & setup screens
        navController.addOnDestinationChangedListener { _, _, _ ->
            isNavExplicitlyHidden = false
            updateBottomNavState()
        }

        updateBottomNavState()

        val isUpdate = intent?.getStringExtra("action") == "show_update" ||
                       intent?.action == AppUpdateHelper.ACTION_SHOW_UPDATE ||
                       intent?.getStringExtra("type") == "app_update"

        if (intent?.getStringExtra("action") == "open_chat") {
            try {
                MyFirebaseMessagingService.dismissChatNotifications(this)
            } catch (_: Exception) {}
            navController.navigate(R.id.chatFragment)
        } else if (isUpdate) {
            try {
                AppUpdateHelper.dismissUpdateNotification(this)
                if (::appUpdateHelper.isInitialized) {
                    appUpdateHelper.checkForUpdates(manualCheck = true)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val navController = navHostFragment?.navController
        val isUpdate = intent.getStringExtra("action") == "show_update" ||
                       intent.action == AppUpdateHelper.ACTION_SHOW_UPDATE ||
                       intent.getStringExtra("type") == "app_update"

        if (intent.getStringExtra("action") == "open_chat") {
            try {
                MyFirebaseMessagingService.dismissChatNotifications(this)
            } catch (_: Exception) {}
            navController?.navigate(R.id.chatFragment)
        } else if (isUpdate) {
            try {
                AppUpdateHelper.dismissUpdateNotification(this)
                if (::appUpdateHelper.isInitialized) {
                    appUpdateHelper.checkForUpdates(manualCheck = true)
                }
            } catch (_: Exception) {}
        }
    }

    private fun setupBackgroundWorkers() {
        val pingRequest = PeriodicWorkRequestBuilder<PingServerWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PingServerWork",
            ExistingPeriodicWorkPolicy.KEEP,
            pingRequest
        )

        val updateRequest = PeriodicWorkRequestBuilder<AppUpdateWorker>(2, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AppUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }

    private fun fetchAndSaveFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            CoroutineScope(Dispatchers.IO).launch {
                val repository = FirestoreRepository()
                repository.updateFcmToken(token)
            }
        }
    }

    private var deliveryListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var incomingCallListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onPause() {
        super.onPause()
        deliveryListener?.remove()
        deliveryListener = null
        incomingCallListener?.remove()
        incomingCallListener = null
        val reminderRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(4, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "InactivityReminderWork",
            ExistingWorkPolicy.REPLACE,
            reminderRequest
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        deliveryListener?.remove()
        deliveryListener = null
        incomingCallListener?.remove()
        incomingCallListener = null
    }

    override fun onResume() {
        super.onResume()
        try {
            if (::appUpdateHelper.isInitialized) {
                appUpdateHelper.resumeUpdates()
                appUpdateHelper.checkForUpdates()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error resuming updates: ${e.message}")
        }
        try {
            WorkManager.getInstance(this).cancelUniqueWork("InactivityReminderWork")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error cancelling work: ${e.message}")
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = FirestoreRepository()
                    val user = repo.getCurrentUser()
                    val cId = user?.coupleId
                    val uid = user?.uid ?: currentUser.uid
                    if (!cId.isNullOrBlank()) {
                        repo.markMessagesDelivered(cId, uid)
                        withContext(Dispatchers.Main) {
                            startDeliveryListener(cId, uid)
                            startIncomingCallListener(cId, uid)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun startDeliveryListener(cId: String, currentUid: String) {
        deliveryListener?.remove()
        deliveryListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("chat_messages")
            .whereEqualTo("coupleId", cId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val undeliveredIds = snapshot.documents.filter { doc ->
                    val senderId = doc.getString("senderId") ?: ""
                    val isDelivered = (doc.getBoolean("isDelivered") == true) || (doc.getBoolean("delivered") == true)
                    senderId.isNotBlank() && senderId != currentUid && !isDelivered
                }.map { it.id }

                if (undeliveredIds.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        FirestoreRepository().markMessagesDeliveredByIds(undeliveredIds)
                    }
                }
            }
    }

    private fun startIncomingCallListener(cId: String, currentUid: String) {
        incomingCallListener?.remove()
        incomingCallListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("video_calls")
            .document(cId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val status = snapshot.getString("status") ?: ""
                val receiverId = snapshot.getString("receiverId") ?: ""
                val callerId = snapshot.getString("callerId") ?: ""
                val callerName = snapshot.getString("callerName") ?: "Your Love"
                val callerAvatar = snapshot.getString("callerAvatar") ?: ""
                val timestamp = snapshot.getLong("timestamp") ?: 0L

                val isRecent = (System.currentTimeMillis() - timestamp) < 60000L

                if (status == "calling" && receiverId == currentUid && isRecent) {
                    // Launch full-screen IncomingCallActivity with ringtone + vibration
                    val intent = Intent(this, com.ourbloom.app.call.IncomingCallActivity::class.java).apply {
                        putExtra(com.ourbloom.app.call.IncomingCallActivity.EXTRA_COUPLE_ID, cId)
                        putExtra(com.ourbloom.app.call.IncomingCallActivity.EXTRA_CALLER_NAME, callerName)
                        putExtra(com.ourbloom.app.call.IncomingCallActivity.EXTRA_CALLER_AVATAR, callerAvatar)
                        putExtra(com.ourbloom.app.call.IncomingCallActivity.EXTRA_CALLER_ID, callerId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                }
            }
    }
}

