package com.ourbloom.app.fcm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DirectFcmSender dispatches high-priority Firebase Cloud Messaging push
 * notifications via the backend server proxy (/api/fcm/send).
 *
 * This keeps Google Cloud Service Account credentials safely on the backend server,
 * preventing credential leaks or security revocations in the Android APK,
 * while maintaining sub-second delivery and background wakeups.
 */
object DirectFcmSender {

    private const val TAG = "DirectFcmSender"
    private const val BASE_URL = "https://our-bloom.onrender.com"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Sends a push notification to the specified device token via backend FCM proxy.
     */
    suspend fun sendPush(
        context: Context,
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            Log.w(TAG, "Cannot send push: partner token is blank")
            return@withContext false
        }

        val endpoint = "$BASE_URL/api/fcm/send"

        try {
            val payload = JSONObject().apply {
                put("token", token)
                put("title", title)
                put("body", body)
                val dataObj = JSONObject()
                data.forEach { (k, v) -> dataObj.put(k, v) }
                put("data", dataObj)
            }

            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string()
                    Log.d(TAG, "FCM push dispatched successfully via server: $responseStr")
                    true
                } else {
                    val err = response.body?.string() ?: ""
                    Log.e(TAG, "FCM dispatch failed [HTTP ${response.code}]: $err")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during FCM dispatch", e)
            false
        }
    }
}
