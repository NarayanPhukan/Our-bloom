package com.ourbloom.app.payment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.ourbloom.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class RazorpayCheckoutActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_VAULT_AMOUNT = "extra_vault_amount"
        const val EXTRA_COUPLE_ID = "extra_couple_id"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_USER_NAME = "extra_user_name"
        const val EXTRA_USER_EMAIL = "extra_user_email"
        const val EXTRA_USER_PHONE = "extra_user_phone"
        const val EXTRA_NOTE = "extra_note"
        const val EXTRA_GOAL_ID = "extra_goal_id"
        const val EXTRA_GOAL_TITLE = "extra_goal_title"

        private const val BASE_URL = "https://our-bloom.onrender.com"
        private const val TAG = "RazorpayCheckout"

        private const val RAZORPAY_KEY_ID = "rzp_live_TmiLSHkOVIFPyZ"
        private const val RAZORPAY_KEY_SECRET = "sUM8e1x0a5UuOiqhMEeCfUOa"

        fun createIntent(
            context: Context,
            amount: Double,
            coupleId: String,
            userId: String,
            userName: String,
            userEmail: String,
            userPhone: String,
            note: String,
            goalId: String?,
            goalTitle: String?,
            vaultAmount: Double = 0.0
        ): Intent {
            return Intent(context, RazorpayCheckoutActivity::class.java).apply {
                putExtra(EXTRA_AMOUNT, amount)
                putExtra(EXTRA_VAULT_AMOUNT, vaultAmount)
                putExtra(EXTRA_COUPLE_ID, coupleId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_USER_NAME, userName)
                putExtra(EXTRA_USER_EMAIL, userEmail)
                putExtra(EXTRA_USER_PHONE, userPhone)
                putExtra(EXTRA_NOTE, note)
                putExtra(EXTRA_GOAL_ID, goalId)
                putExtra(EXTRA_GOAL_TITLE, goalTitle)
            }
        }
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutLoading: LinearLayout
    private lateinit var tvLoadingStatus: TextView
    private lateinit var layoutWaiting: LinearLayout
    private lateinit var tvWaitingAmount: TextView
    private lateinit var btnVerifyPayment: MaterialButton
    private lateinit var btnReopenGateway: MaterialButton

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var payableAmount: Double = 0.0
    private var vaultAmount: Double = 0.0
    private var coupleId: String = ""
    private var userId: String = ""
    private var userName: String = ""
    private var userEmail: String = ""
    private var userPhone: String = ""
    private var note: String = ""
    private var goalId: String? = null
    private var goalTitle: String? = null

    private var currentTxnId: String = ""
    private var currentPaymentUrl: String = ""
    private var currentPaymentLinkId: String = ""
    private var isCompleted: Boolean = false
    private var hasLaunchedCustomTab: Boolean = false
    private var isCheckingStatus: Boolean = false
    private var lastErrorMessage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_razorpay_checkout)

        payableAmount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        vaultAmount = intent.getDoubleExtra(EXTRA_VAULT_AMOUNT, 0.0)
        coupleId = intent.getStringExtra(EXTRA_COUPLE_ID) ?: ""
        userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
        userName = intent.getStringExtra(EXTRA_USER_NAME) ?: "Partner"
        userEmail = intent.getStringExtra(EXTRA_USER_EMAIL) ?: ""
        userPhone = intent.getStringExtra(EXTRA_USER_PHONE) ?: ""
        note = intent.getStringExtra(EXTRA_NOTE) ?: ""
        goalId = intent.getStringExtra(EXTRA_GOAL_ID)
        goalTitle = intent.getStringExtra(EXTRA_GOAL_TITLE)

        webView = findViewById(R.id.web_view_razorpay)
        progressBar = findViewById(R.id.progress_razorpay)
        layoutLoading = findViewById(R.id.layout_razorpay_loading)
        tvLoadingStatus = findViewById(R.id.tv_razorpay_loading_status)
        layoutWaiting = findViewById(R.id.layout_razorpay_waiting)
        tvWaitingAmount = findViewById(R.id.tv_waiting_amount)
        btnVerifyPayment = findViewById(R.id.btn_verify_payment)
        btnReopenGateway = findViewById(R.id.btn_reopen_gateway)

        tvWaitingAmount.text = String.format(Locale.getDefault(), "₹%.2f", payableAmount)

        findViewById<ImageButton>(R.id.btn_close_razorpay).setOnClickListener {
            handleCancel()
        }

        findViewById<View>(R.id.btn_cancel_waiting).setOnClickListener {
            handleCancel()
        }

        btnVerifyPayment.setOnClickListener {
            checkPaymentStatus(userInitiated = true)
        }

        btnReopenGateway.setOnClickListener {
            if (currentPaymentUrl.isNotBlank()) {
                launchCustomTab(currentPaymentUrl)
            } else {
                initiatePaymentLinkCreation()
            }
        }

        setupFallbackWebView()
        handleIncomingDeepLink(intent)

        if (currentTxnId.isBlank()) {
            initiatePaymentLinkCreation()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingDeepLink(intent)
    }

    private fun handleIncomingDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        Log.d(TAG, "Incoming deep link: $uri")

        if (uri.scheme == "ourbloom" && uri.host == "razorpay-return") {
            val txnidParam = uri.getQueryParameter("txnid")
            if (!txnidParam.isNullOrBlank()) {
                currentTxnId = txnidParam
            }
            checkPaymentStatus(userInitiated = false)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasLaunchedCustomTab && !isCompleted && !isCheckingStatus && currentTxnId.isNotBlank()) {
            checkPaymentStatus(userInitiated = false)
        }
    }

    private fun initiatePaymentLinkCreation() {
        tvLoadingStatus.text = "Opening Razorpay UPI Gateway..."
        layoutLoading.visibility = View.VISIBLE
        layoutWaiting.visibility = View.GONE
        webView.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            // Attempt 1: Direct Razorpay Payment Link API (instant 200ms creation, no cold-start delay)
            var linkData = createPaymentLinkDirectly()

            // Attempt 2 (Fallback): Server payment link endpoint
            if (linkData == null) {
                Log.i(TAG, "Direct Razorpay creation unfulfilled, trying via backend server...")
                linkData = createPaymentLinkViaServer()
            }

            if (linkData != null) {
                currentPaymentUrl = linkData.first
                currentTxnId = linkData.second
                withContext(Dispatchers.Main) {
                    launchCustomTab(currentPaymentUrl)
                }
            } else {
                withContext(Dispatchers.Main) {
                    val errorMsg = if (lastErrorMessage.isNotBlank()) {
                        "Payment initialization failed: $lastErrorMessage"
                    } else {
                        "Unable to initialize Razorpay gateway. Please check your internet connection and try again."
                    }
                    showErrorDialog(errorMsg)
                }
            }
        }
    }

    private fun createPaymentLinkViaServer(): Pair<String, String>? {
        return try {
            val jsonPayload = JSONObject().apply {
                put("amount", payableAmount)
                put("vaultAmountPaise", Math.round(vaultAmount * 100))
                put("coupleId", coupleId)
                put("userId", userId)
                put("userName", userName)
                put("email", userEmail)
                put("phone", userPhone)
                put("goalId", goalId ?: "")
                put("note", note)
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/api/razorpay/create-payment-link")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val data = JSONObject(responseBody)
                val paymentUrl = data.getString("paymentUrl")
                val txnid = data.getString("txnid")
                currentPaymentLinkId = data.optString("paymentLinkId", "")
                Pair(paymentUrl, txnid)
            } else {
                val errDesc = try {
                    JSONObject(responseBody).optString("error")
                } catch (_: Exception) { null }
                if (!errDesc.isNullOrBlank()) {
                    lastErrorMessage = errDesc
                }
                Log.w(TAG, "Server create-payment-link returned HTTP ${response.code}: $responseBody")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Server create-payment-link exception: ${e.message}")
            null
        }
    }

    private fun createPaymentLinkDirectly(): Pair<String, String>? {
        return try {
            val amountPaise = Math.round(payableAmount * 100)
            val generatedTxnId = "RZP_${System.currentTimeMillis()}_${(1000..9999).random()}"
            val authCredentials = "$RAZORPAY_KEY_ID:$RAZORPAY_KEY_SECRET"
            val authHeader = "Basic " + Base64.encodeToString(authCredentials.toByteArray(), Base64.NO_WRAP)

            val payload = JSONObject().apply {
                put("amount", amountPaise)
                put("currency", "INR")
                put("description", "OurBloom Couple Vault Deposit")
                val customer = JSONObject().apply {
                    put("name", userName.ifBlank { "Partner" })
                    put("email", if (userEmail.contains("@")) userEmail else "partner@ourbloom.app")
                    val cleanDigits = userPhone.replace(Regex("\\D"), "")
                    if (cleanDigits.length >= 10) {
                        put("contact", "+91" + cleanDigits.takeLast(10))
                    }
                }
                put("customer", customer)
                put("notify", JSONObject().apply {
                    put("sms", false)
                    put("email", false)
                })
                put("reminder_enable", false)
                put("notes", JSONObject().apply {
                    put("coupleId", coupleId)
                    put("userId", userId)
                    put("txnid", generatedTxnId)
                    put("goalId", goalId ?: "")
                })
                put("callback_url", "https://our-bloom-gamma.vercel.app/payment-return.html?txnid=$generatedTxnId")
                put("callback_method", "get")
            }

            var request = Request.Builder()
                .url("https://api.razorpay.com/v1/payment_links")
                .header("Authorization", authHeader)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            var response = httpClient.newCall(request).execute()
            var responseBody = response.body?.string() ?: ""

            // Fallback retry without callback_url if callback_url field is ever rejected
            if (!response.isSuccessful && response.code == 400 && responseBody.contains("callback_url")) {
                Log.w(TAG, "Retrying direct Razorpay link creation without callback_url: $responseBody")
                payload.remove("callback_url")
                payload.remove("callback_method")
                request = Request.Builder()
                    .url("https://api.razorpay.com/v1/payment_links")
                    .header("Authorization", authHeader)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                response = httpClient.newCall(request).execute()
                responseBody = response.body?.string() ?: ""
            }

            if (response.isSuccessful) {
                val data = JSONObject(responseBody)
                val shortUrl = data.getString("short_url")
                val linkId = data.optString("id", "")
                currentPaymentLinkId = linkId
                Log.d(TAG, "Direct Razorpay Payment Link created: $shortUrl (id: $linkId)")
                Pair(shortUrl, generatedTxnId)
            } else {
                val errDesc = try {
                    JSONObject(responseBody).optJSONObject("error")?.optString("description")
                } catch (_: Exception) { null }
                lastErrorMessage = errDesc ?: "HTTP ${response.code}: $responseBody"
                Log.e(TAG, "Direct Razorpay creation failed (HTTP ${response.code}): $responseBody")
                null
            }
        } catch (e: Exception) {
            lastErrorMessage = e.message ?: "Network error"
            Log.e(TAG, "Exception during direct Razorpay link creation: ${e.message}", e)
            null
        }
    }

    private fun launchCustomTab(url: String) {
        layoutLoading.visibility = View.GONE
        layoutWaiting.visibility = View.VISIBLE
        tvWaitingAmount.text = String.format(Locale.getDefault(), "₹%.2f", payableAmount)

        try {
            val builder = CustomTabsIntent.Builder()
            builder.setShowTitle(true)
            val colorSchemeParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(Color.parseColor("#E85D75"))
                .build()
            builder.setDefaultColorSchemeParams(colorSchemeParams)
            builder.setStartAnimations(this, android.R.anim.fade_in, android.R.anim.fade_out)
            builder.setExitAnimations(this, android.R.anim.fade_in, android.R.anim.fade_out)
            val customTabsIntent = builder.build()
            customTabsIntent.intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP

            customTabsIntent.launchUrl(this, Uri.parse(url))
            hasLaunchedCustomTab = true
            Log.d(TAG, "Chrome Custom Tab launched for URL: $url")
        } catch (e: Exception) {
            Log.w(TAG, "CustomTabs failed, falling back to embedded WebView: ${e.message}")
            openInFallbackWebView(url)
        }
    }

    private fun checkPaymentStatus(userInitiated: Boolean) {
        if (isCompleted || isCheckingStatus || currentTxnId.isBlank()) return
        isCheckingStatus = true

        if (userInitiated) {
            btnVerifyPayment.isEnabled = false
            btnVerifyPayment.text = "Verifying with Gateway..."
            progressBar.visibility = View.VISIBLE
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var isPaid = false

            // 1. Try server check-status
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/api/razorpay/check-status?txnid=$currentTxnId")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val data = JSONObject(responseBody)
                    val completed = data.optBoolean("completed", false)
                    val status = data.optString("status", "")
                    if (completed || status.equals("COMPLETED", ignoreCase = true)) {
                        isPaid = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Server check-status error: ${e.message}")
            }

            // 2. Direct Razorpay verification fallback
            if (!isPaid && currentPaymentLinkId.isNotBlank()) {
                try {
                    val authCredentials = "$RAZORPAY_KEY_ID:$RAZORPAY_KEY_SECRET"
                    val authHeader = "Basic " + Base64.encodeToString(authCredentials.toByteArray(), Base64.NO_WRAP)
                    val rzpRequest = Request.Builder()
                        .url("https://api.razorpay.com/v1/payment_links/$currentPaymentLinkId")
                        .header("Authorization", authHeader)
                        .get()
                        .build()

                    val rzpResponse = httpClient.newCall(rzpRequest).execute()
                    val rzpBody = rzpResponse.body?.string() ?: ""
                    if (rzpResponse.isSuccessful) {
                        val rzpData = JSONObject(rzpBody)
                        if (rzpData.optString("status") == "paid") {
                            isPaid = true
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Direct Razorpay status check error: ${e.message}")
                }
            }

            if (isPaid) {
                withContext(Dispatchers.Main) {
                    onPaymentVerifiedSuccess(currentPaymentLinkId.ifBlank { currentTxnId })
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                isCheckingStatus = false
                progressBar.visibility = View.GONE
                btnVerifyPayment.isEnabled = true
                btnVerifyPayment.text = "I Have Completed Payment ⚡"

                if (userInitiated) {
                    Toast.makeText(
                        this@RazorpayCheckoutActivity,
                        "Payment approval is still processing. Please allow a few seconds for confirmation.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun onPaymentVerifiedSuccess(paymentId: String) {
        if (isCompleted) return
        isCompleted = true

        progressBar.visibility = View.VISIBLE
        Toast.makeText(this, "Deposit Verified! Added to Couple's Vault 🌸", Toast.LENGTH_LONG).show()

        val exactVaultCredit = if (vaultAmount > 0.0) vaultAmount else (Math.round((payableAmount / 1.02) * 100.0) / 100.0).coerceAtLeast(1.0)

        val resultIntent = Intent().apply {
            putExtra("txnid", currentTxnId)
            putExtra("reference", paymentId)
            putExtra("paymentId", paymentId)
            putExtra("amount", payableAmount)
            putExtra("vaultAmount", exactVaultCredit)
            putExtra("coupleId", coupleId)
            putExtra("userId", userId)
            putExtra("userName", userName)
            putExtra("note", note)
            putExtra("goalId", goalId)
            putExtra("goalTitle", goalTitle)
            putExtra("paymentMethod", "Razorpay")
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupFallbackWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            val defaultUa = userAgentString
            userAgentString = defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleUrlScheme(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                return handleUrlScheme(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                layoutLoading.visibility = View.GONE
            }
        }
    }

    private fun openInFallbackWebView(url: String) {
        layoutLoading.visibility = View.VISIBLE
        layoutWaiting.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(url)
    }

    private fun handleUrlScheme(url: String): Boolean {
        val lower = url.lowercase()

        if (lower.startsWith("ourbloom://razorpay-return")) {
            val uri = Uri.parse(url)
            val txnidParam = uri.getQueryParameter("txnid")
            if (!txnidParam.isNullOrBlank()) {
                currentTxnId = txnidParam
            }
            checkPaymentStatus(userInitiated = false)
            return true
        }

        if (lower.startsWith("upi://") ||
            lower.startsWith("gpay://") ||
            lower.startsWith("phonepe://") ||
            lower.startsWith("paytmmp://") ||
            lower.startsWith("bhim://") ||
            lower.startsWith("credpay://")
        ) {
            return launchUpiIntent(url)
        }

        if (lower.startsWith("intent://")) {
            return launchGenericIntent(url)
        }

        return false
    }

    private fun launchUpiIntent(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val upiIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val chooser = Intent.createChooser(upiIntent, "Pay via UPI App").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No UPI payment app found. Please select Card or NetBanking.", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching UPI intent: ${e.message}", e)
            false
        }
    }

    private fun launchGenericIntent(url: String): Boolean {
        return try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch generic intent: ${e.message}")
            false
        }
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Checkout Error")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun handleCancel() {
        if (isCompleted) return
        AlertDialog.Builder(this)
            .setTitle("Cancel Deposit?")
            .setMessage("Are you sure you want to exit? If you have already approved payment in your UPI app, tap 'Check Status'.")
            .setPositiveButton("Exit") { _, _ ->
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .setNeutralButton("Check Status") { _, _ ->
                checkPaymentStatus(userInitiated = true)
            }
            .setNegativeButton("Continue", null)
            .show()
    }
}
