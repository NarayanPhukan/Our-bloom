package com.ourbloom.admin

import android.app.Application
import com.google.firebase.FirebaseApp

class AdminApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        com.ourbloom.admin.bugs.AdminBugRadar.init(this)
        com.ourbloom.admin.profile.AdminProfileRepository.init(this)
    }
}
