package com.ourbloom.app.fcm

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit

/**
 * DirectFcmSender enables the OurBloom Android client to dispatch high-priority
 * Firebase Cloud Messaging notifications directly to the partner's device
 * via the Google FCM v1 HTTP API (https://fcm.googleapis.com/v1/projects/{project_id}/messages:send).
 *
 * This completely eliminates dependency on sleeping Render server instances, guaranteeing
 * instant sub-second delivery, background wakeups, and double ticks 24/7.
 */
object DirectFcmSender {

    private const val TAG = "DirectFcmSender"
    private const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var cachedProjectId: String? = null
    private var cachedClientEmail: String? = null
    private var cachedPrivateKey: PrivateKey? = null

    @Volatile
    private var cachedAccessToken: String? = null
    @Volatile
    private var tokenExpiryEpochSec: Long = 0L

    /**
     * Loads and parses credentials from assets/firebase-service-account.json.
     */
    @Synchronized
    private fun ensureCredentialsLoaded(context: Context): Boolean {
        if (cachedPrivateKey != null && cachedClientEmail != null && cachedProjectId != null) {
            return true
        }

        return try {
            val inputStream = context.assets.open("firebase-service-account.json")
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val jsonString = reader.readText()
            reader.close()
            inputStream.close()

            val json = JSONObject(jsonString)
            cachedProjectId = json.optString("project_id", "our-bloom")
            cachedClientEmail = json.getString("client_email")

            val pemKey = json.getString("private_key")
            cachedPrivateKey = parsePemPrivateKey(pemKey)

            Log.d(TAG, "Successfully loaded Firebase service account for $cachedClientEmail (project: $cachedProjectId)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load firebase-service-account.json from assets", e)
            false
        }
    }

    private fun parsePemPrivateKey(pem: String): PrivateKey {
        val cleanKey = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s+".toRegex(), "")
        val keyBytes = Base64.decode(cleanKey, Base64.DEFAULT)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePrivate(spec)
    }

    /**
     * Gets a valid Google OAuth2 Bearer token, reusing the cached token if valid.
     */
    private suspend fun getAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        val nowSec = System.currentTimeMillis() / 1000
        if (!cachedAccessToken.isNullOrBlank() && nowSec < (tokenExpiryEpochSec - 300)) {
            return@withContext cachedAccessToken
        }

        if (!ensureCredentialsLoaded(context)) {
            Log.e(TAG, "Cannot obtain access token: credentials not loaded")
            return@withContext null
        }

        val clientEmail = cachedClientEmail ?: return@withContext null
        val privateKey = cachedPrivateKey ?: return@withContext null

        try {
            val headerJson = """{"alg":"RS256","typ":"JWT"}"""
            val headerB64 = Base64.encodeToString(
                headerJson.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val expSec = nowSec + 3600
            val claimJson = """{"iss":"$clientEmail","scope":"$FCM_SCOPE","aud":"$TOKEN_URL","exp":$expSec,"iat":$nowSec}"""
            val claimB64 = Base64.encodeToString(
                claimJson.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val dataToSign = "$headerB64.$claimB64".toByteArray(Charsets.UTF_8)
            val signer = Signature.getInstance("SHA256withRSA")
            signer.initSign(privateKey)
            signer.update(dataToSign)
            val signatureB64 = Base64.encodeToString(
                signer.sign(),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val assertionJwt = "$headerB64.$claimB64.$signatureB64"

            val formBody = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                .add("assertion", assertionJwt)
                .build()

            val tokenReq = Request.Builder()
                .url(TOKEN_URL)
                .post(formBody)
                .build()

            httpClient.newCall(tokenReq).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "OAuth token exchange failed [HTTP ${response.code}]: $errBody")
                    return@withContext null
                }

                val resString = response.body?.string() ?: return@withContext null
                val resJson = JSONObject(resString)
                val token = resJson.getString("access_token")
                val expiresIn = resJson.optLong("expires_in", 3600L)

                cachedAccessToken = token
                tokenExpiryEpochSec = nowSec + expiresIn
                Log.d(TAG, "Obtained fresh Google OAuth2 access token (expires in ${expiresIn}s)")
                return@withContext token
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during OAuth2 access token exchange", e)
            null
        }
    }

    /**
     * Sends a high-priority FCM v1 push notification directly to the specified device token.
     */
    suspend fun sendPush(
        context: Context,
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            Log.w(TAG, "Cannot send direct FCM: partner token is blank")
            return@withContext false
        }

        val accessToken = getAccessToken(context)
        if (accessToken.isNullOrBlank()) {
            Log.e(TAG, "Cannot send direct FCM: failed to obtain access token")
            return@withContext false
        }

        val projectId = cachedProjectId ?: "our-bloom"
        val endpoint = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"

        try {
            val payload = JSONObject().apply {
                val msg = JSONObject().apply {
                    put("token", token)

                    val type = data["type"]
                    val isCall = type == "video_call"
                    val isHeartbeat = type == "heartbeat"
                    val channelId = when {
                        isCall -> "ourbloom_call_channel"
                        isHeartbeat -> "ourbloom_heartbeat_channel"
                        else -> "ourbloom_chat_heads_up_v3"
                    }

                    // Top-level notification block: Guarantees Google Play Services displays
                    // the notification directly on Android status bar even if the app process is closed/killed/in Doze
                    val notifObj = JSONObject().apply {
                        put("title", title)
                        put("body", body)
                    }
                    put("notification", notifObj)

                    val dataObj = JSONObject().apply {
                        put("title", title)
                        put("body", body)
                        data.forEach { (k, v) -> put(k, v) }
                    }
                    put("data", dataObj)

                    val androidObj = JSONObject().apply {
                        put("priority", "HIGH")
                        val androidNotif = JSONObject().apply {
                            put("channel_id", channelId)
                            put("notification_priority", "PRIORITY_MAX")
                            put("visibility", "PUBLIC")
                            put("default_sound", true)
                            put("default_vibrate_timings", true)
                        }
                        put("notification", androidNotif)
                    }
                    put("android", androidObj)
                }
                put("message", msg)
            }

            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string()
                    Log.d(TAG, "Direct FCM push dispatched successfully: $responseStr")
                    true
                } else {
                    val err = response.body?.string() ?: ""
                    Log.e(TAG, "Direct FCM dispatch failed [HTTP ${response.code}]: $err")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during direct FCM dispatch", e)
            false
        }
    }
}
