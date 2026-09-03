# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in defaultProguardFile('proguard-android-optimize.txt')

# Keep Data Models
-keep class com.ourbloom.app.data.** { *; }
-keep class com.ourbloom.app.chat.** { *; }

# Firebase Keep Rules
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-dontwarn kotlinx.coroutines.**

# OkHttp & Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# AndroidX Navigation
-keepclassmembers class * extends androidx.navigation.Navigator {
    public <init>(...);
}

# WorkManager
-keep class androidx.work.Worker { *; }
-keep class androidx.work.ListenableWorker { *; }
-keep class androidx.work.CoroutineWorker { *; }
-keep class com.ourbloom.app.workers.** { *; }
