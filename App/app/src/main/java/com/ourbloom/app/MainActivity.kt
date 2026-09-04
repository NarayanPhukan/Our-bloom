package com.ourbloom.app

import android.os.Bundle
import android.util.Log
import android.Manifest
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
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.updates.AppUpdateHelper
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

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                fetchAndSaveFcmToken()
            }
        }
    }
    
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        
        bottomNav.setupWithNavController(navController)
        
        // Hide bottom navigation on auth screens
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.loginFragment, R.id.registerFragment -> {
                    bottomNav.visibility = View.GONE
                }
                else -> {
                    bottomNav.visibility = View.VISIBLE
                }
            }
        }

        if (intent?.getStringExtra("action") == "open_chat") {
            navController.navigate(R.id.chatFragment)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val navController = navHostFragment?.navController
        if (intent.getStringExtra("action") == "open_chat") {
            navController?.navigate(R.id.chatFragment)
        }
    }

    private fun setupBackgroundWorkers() {
        val pingRequest = PeriodicWorkRequestBuilder<PingServerWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PingServerWork",
            ExistingPeriodicWorkPolicy.KEEP,
            pingRequest
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

    override fun onPause() {
        super.onPause()
        deliveryListener?.remove()
        deliveryListener = null
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
    }

    override fun onResume() {
        super.onResume()
        try {
            appUpdateHelper.resumeUpdates()
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
}
