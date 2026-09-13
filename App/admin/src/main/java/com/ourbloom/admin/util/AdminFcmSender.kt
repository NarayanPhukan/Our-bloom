package com.ourbloom.admin.util

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

object AdminFcmSender {

    private const val TAG = "AdminFcmSender"
    private const val BASE_URL = "https://our-bloom.onrender.com"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun sendPush(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            Log.w(TAG, "Cannot send push: token is blank")
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
                if (!response.isSuccessful) {
                    Log.w(TAG, "Backend FCM proxy error code=${response.code}")
                    false
                } else {
                    Log.d(TAG, "Push successfully dispatched via backend FCM proxy")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch push notification: ${e.message}")
            false
        }
    }
}
