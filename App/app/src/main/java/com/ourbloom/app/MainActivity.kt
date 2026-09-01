package com.ourbloom.app

import android.os.Bundle
import android.util.Log
import android.Manifest
import android.os.Build
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.messaging.FirebaseMessaging
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.updates.AppUpdateHelper
import com.ourbloom.app.workers.PingServerWorker
import com.ourbloom.app.workers.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var appUpdateHelper: AppUpdateHelper

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        // Handle permission results if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appUpdateHelper = AppUpdateHelper(this)
        appUpdateHelper.checkForUpdates()
        
        requestPermissions.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
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
            if (task.isSuccessful) {
                val token = task.result
                Log.d("MainActivity", "FCM Token: $token")
                CoroutineScope(Dispatchers.IO).launch {
                    FirestoreRepository().updateFcmToken(token)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val reminderRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(4, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "InactivityReminderWork",
            ExistingWorkPolicy.REPLACE,
            reminderRequest
        )
    }

    override fun onResume() {
        super.onResume()
        appUpdateHelper.resumeUpdates()
        WorkManager.getInstance(this).cancelUniqueWork("InactivityReminderWork")
    }
}
