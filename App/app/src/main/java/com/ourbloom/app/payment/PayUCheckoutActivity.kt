package com.ourbloom.app.payment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.ourbloom.app.BuildConfig
import com.ourbloom.app.R
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.*

class PayUCheckoutActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_COUPLE_ID = "extra_couple_id"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_USER_NAME = "extra_user_name"
        const val EXTRA_USER_EMAIL = "extra_user_email"
        const val EXTRA_USER_PHONE = "extra_user_phone"
        const val EXTRA_NOTE = "extra_note"
        const val EXTRA_GOAL_ID = "extra_goal_id"
        const val EXTRA_GOAL_TITLE = "extra_goal_title"

        private const val LIVE_ACTION_URL = "https://secure.payu.in/_payment"
        private const val TEST_ACTION_URL = "https://test.payu.in/_payment"

        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

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
            goalTitle: String?
        ): Intent {
            return Intent(context, PayUCheckoutActivity::class.java).apply {
                putExtra(EXTRA_AMOUNT, amount)
                putExtra(EXTRA_COUPLE_ID, coupleId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_USER_NAME, userName)
                putExtra(EXTRA_USER_EMAIL, userEmail)
                putExtra(EXTRA_USER_PHONE, userPhone)
                putExtra(EXTRA_NOTE, note)
                putExtra(EXTRA_GOAL_ID, goalId ?: "")
                putExtra(EXTRA_GOAL_TITLE, goalTitle ?: "")
            }
        }
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutError: View
    private lateinit var btnClose: ImageButton
    private lateinit var chipModeToggle: Chip

    private var defaultMobileUserAgent: String = ""
    private var isDesktopMode = false

    private var amount: Double = 0.0
    private var coupleId: String = ""
    private var userId: String = ""
    private var userName: String = ""
    private var userEmail: String = ""
    private var userPhone: String = ""
    private var note: String = ""
    private var goalId: String = ""
    private var goalTitle: String = ""

    private var isCompleted = false
    private var currentTxnId: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payu_checkout)

        amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        coupleId = intent.getStringExtra(EXTRA_COUPLE_ID) ?: ""
        userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
        userName = intent.getStringExtra(EXTRA_USER_NAME) ?: "Partner"
        userEmail = intent.getStringExtra(EXTRA_USER_EMAIL) ?: ""
        userPhone = intent.getStringExtra(EXTRA_USER_PHONE) ?: ""
        note = intent.getStringExtra(EXTRA_NOTE) ?: ""
        goalId = intent.getStringExtra(EXTRA_GOAL_ID) ?: ""
        goalTitle = intent.getStringExtra(EXTRA_GOAL_TITLE) ?: ""

        if (amount <= 0.0 || coupleId.isBlank()) {
            Toast.makeText(this, "Invalid payment parameters", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // On emulators or devices without UPI apps, default to QR code/desktop mode so user can scan & pay
        val upiAvailable = isAnyUpiAppInstalled()
        isDesktopMode = !upiAvailable

        initViews()
        setupWebView()
        initiatePayment()
    }

    private fun initViews() {
        webView = findViewById(R.id.web_view_checkout)
        progressBar = findViewById(R.id.progress_checkout)
        layoutError = findViewById(R.id.layout_checkout_error)
        btnClose = findViewById(R.id.btn_close_checkout)
        chipModeToggle = findViewById(R.id.chip_mode_toggle)

        defaultMobileUserAgent = webView.settings.userAgentString

        updateToggleChipText()

        btnClose.setOnClickListener {
            onBackPressed()
        }

        chipModeToggle.setOnClickListener {
            enableDesktopMode(!isDesktopMode)
        }

        findViewById<View>(R.id.btn_retry_checkout).setOnClickListener {
            layoutError.visibility = View.GONE
            initiatePayment()
        }
    }

    private fun updateToggleChipText() {
        if (isDesktopMode) {
            chipModeToggle.text = "📱 Mobile Apps"
        } else {
            chipModeToggle.text = "📷 Scan QR / VPA"
        }
    }

    private fun enableDesktopMode(enable: Boolean) {
        isDesktopMode = enable
        updateToggleChipText()
        if (enable) {
            webView.settings.userAgentString = DESKTOP_USER_AGENT
            Toast.makeText(this, "Switched to QR Code & UPI ID mode. Scan with phone!", Toast.LENGTH_SHORT).show()
        } else {
            webView.settings.userAgentString = defaultMobileUserAgent
            Toast.makeText(this, "Switched to Mobile UPI Apps mode", Toast.LENGTH_SHORT).show()
        }
        initiatePayment()
    }

    inner class PaymentBridge {
        @android.webkit.JavascriptInterface
        fun onPaymentSuccess(ref: String?) {
            runOnUiThread {
                onPaymentApproved(ref)
            }
        }

        @android.webkit.JavascriptInterface
        fun onPaymentFailure(msg: String?) {
            runOnUiThread {
                onPaymentDeclined()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = true

        // Keep false so window.open() handles in the existing WebView without swallowing redirects
        settings.setSupportMultipleWindows(false)

        if (isDesktopMode) {
            settings.userAgentString = DESKTOP_USER_AGENT
        } else {
            settings.userAgentString = defaultMobileUserAgent
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(PaymentBridge(), "PayUBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress >= 90) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("PayUCheckout", "onPageStarted: $url")
                if (url != null) {
                    if (isSuccessUrl(url)) {
                        onPaymentApproved()
                        return
                    }
                    if (isFailureUrl(url)) {
                        onPaymentDeclined()
                        return
                    }
                }
                progressBar.visibility = View.VISIBLE
                layoutError.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("PayUCheckout", "onPageFinished: $url")
                if (url != null) {
                    if (isSuccessUrl(url)) {
                        onPaymentApproved()
                        return
                    }
                    if (isFailureUrl(url)) {
                        onPaymentDeclined()
                        return
                    }
                }
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val failingUrl = request?.url?.toString() ?: ""
                Log.w("PayUCheckout", "WebView error on $failingUrl")
                if (isSuccessUrl(failingUrl)) {
                    onPaymentApproved()
                    return
                }
                if (request?.isForMainFrame == true && !isSuccessUrl(failingUrl)) {
                    progressBar.visibility = View.GONE
                    layoutError.visibility = View.VISIBLE
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleUrl(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                return handleUrl(url)
            }
        }
    }

    private fun isSuccessUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains("/api/payu/success") ||
               lower.contains("ourbloom.app/payment/success") ||
               lower.contains("/payment/success") ||
               lower.contains("payu.in/success") ||
               lower.contains("status=success")
    }

    private fun isFailureUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains("/api/payu/failure") ||
               lower.contains("ourbloom.app/payment/failure") ||
               lower.contains("/payment/failure") ||
               lower.contains("payu.in/failure") ||
               lower.contains("status=failure")
    }

    private fun handleUrl(url: String): Boolean {
        Log.d("PayUCheckout", "Navigation URL: $url")

        // Intercept Success Callback
        if (isSuccessUrl(url)) {
            onPaymentApproved()
            return true
        }

        // Intercept Failure Callback
        if (isFailureUrl(url)) {
            onPaymentDeclined()
            return true
        }

        // Handle standard UPI URL (upi://pay?...)
        if (url.startsWith("upi://", ignoreCase = true)) {
            return launchUpiIntent(url)
        }

        // Handle Android Intent URL (intent://...#Intent;scheme=...;end)
        if (url.startsWith("intent://", ignoreCase = true)) {
            return launchGenericIntent(url)
        }

        // Handle direct app schemes (gpay://, phonepe://, paytmmp://, etc.)
        if (url.startsWith("gpay://", ignoreCase = true) ||
            url.startsWith("phonepe://", ignoreCase = true) ||
            url.startsWith("paytmmp://", ignoreCase = true) ||
            url.startsWith("credpay://", ignoreCase = true) ||
            url.startsWith("bhim://", ignoreCase = true) ||
            url.startsWith("mobikwik://", ignoreCase = true) ||
            url.startsWith("amazonpay://", ignoreCase = true)
        ) {
            return launchSpecificAppIntent(url)
        }

        return false
    }

    private fun isAnyUpiAppInstalled(): Boolean {
        return try {
            val uri = Uri.parse("upi://pay")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            val activities = packageManager.queryIntentActivities(intent, 0)
            activities.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun launchUpiIntent(url: String): Boolean {
        Log.d("PayUCheckout", "Triggering UPI Payment intent for: $url")
        return try {
            val uri = Uri.parse(url)
            val upiIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val activities = packageManager.queryIntentActivities(upiIntent, 0)
            if (activities.isNotEmpty()) {
                val chooser = Intent.createChooser(upiIntent, "Pay via UPI App").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(chooser)
                true
            } else {
                showNoUpiAppDialog(url)
                true
            }
        } catch (e: ActivityNotFoundException) {
            showNoUpiAppDialog(url)
            true
        } catch (e: Exception) {
            Log.e("PayUCheckout", "Error launching UPI intent: ${e.message}", e)
            showNoUpiAppDialog(url)
            true
        }
    }

    private fun launchGenericIntent(url: String): Boolean {
        Log.d("PayUCheckout", "Triggering generic Android intent for: $url")
        try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                if (action.isNullOrBlank()) {
                    action = Intent.ACTION_VIEW
                }
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // 1. Try launching specific package if intent targeted one and it is installed
            try {
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    return true
                }
            } catch (_: Exception) {}

            // 2. If it's a UPI scheme, check if any installed UPI app can handle it
            val dataUri = intent.data
            if (dataUri != null && dataUri.scheme.equals("upi", ignoreCase = true)) {
                val genericUpiIntent = Intent(Intent.ACTION_VIEW, dataUri).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val activities = packageManager.queryIntentActivities(genericUpiIntent, 0)
                if (activities.isNotEmpty()) {
                    val chooser = Intent.createChooser(genericUpiIntent, "Pay via UPI App").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(chooser)
                    return true
                } else {
                    showNoUpiAppDialog(dataUri.toString())
                    return true
                }
            }

            // 3. Try browser fallback URL if provided
            val fallbackUrl = intent.getStringExtra("browser_fallback_url")
            if (!fallbackUrl.isNullOrBlank()) {
                webView.loadUrl(fallbackUrl)
                return true
            }

            showNoUpiAppDialog(url)
            return true
        } catch (e: ActivityNotFoundException) {
            showNoUpiAppDialog(url)
            return true
        } catch (e: Exception) {
            Log.e("PayUCheckout", "Failed to parse intent URL: ${e.message}", e)
            showNoUpiAppDialog(url)
            return true
        }
    }

    private fun launchSpecificAppIntent(url: String): Boolean {
        Log.d("PayUCheckout", "Triggering app scheme: $url")
        try {
            val uri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val activities = packageManager.queryIntentActivities(intent, 0)
            if (activities.isNotEmpty()) {
                startActivity(intent)
                return true
            } else {
                showNoUpiAppDialog(url)
                return true
            }
        } catch (e: ActivityNotFoundException) {
            showNoUpiAppDialog(url)
            return true
        } catch (e: Exception) {
            Log.e("PayUCheckout", "Failed to launch specific app URL: ${e.message}", e)
            showNoUpiAppDialog(url)
            return true
        }
    }

    private fun showNoUpiAppDialog(upiUrl: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("No UPI App on this Device")
                .setMessage("No UPI app (Google Pay, PhonePe, Paytm) was found installed on this device or emulator.\n\nTo complete payment:\n• Switch to QR Code mode to scan with your phone\n• Or copy the UPI payment link\n• Or pay using Card / NetBanking below")
                .setPositiveButton("📷 Scan QR with Phone") { _, _ ->
                    enableDesktopMode(true)
                }
                .setNeutralButton("📋 Copy Link") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("UPI URL", upiUrl)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "UPI payment URL copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Dismiss", null)
                .show()
        }
    }

    private fun initiatePayment() {
        progressBar.visibility = View.VISIBLE
        layoutError.visibility = View.GONE

        // Read Live Credentials from BuildConfig with fallbacks
        val payuKey = BuildConfig.PAYU_KEY.ifBlank { "qSYSCh" }
        val payuSalt = BuildConfig.PAYU_SALT.ifBlank { "ILJi7wHiIDwmOFT1VAWBh6hCfSpkVYfA" }
        val isLiveMode = BuildConfig.PAYU_MODE.equals("live", ignoreCase = true)
        val actionUrl = if (isLiveMode) LIVE_ACTION_URL else TEST_ACTION_URL

        val txnid = "OB_${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "").take(6)}"
        currentTxnId = txnid
        val formattedAmount = String.format(Locale.US, "%.2f", amount)
        val cleanFirstName = userName.replace(Regex("[^a-zA-Z0-9]"), "").trim().ifBlank { "Partner" }
        val cleanEmail = if (userEmail.isNotBlank() && userEmail.contains("@")) userEmail.trim() else "support@ourbloom.app"
        val cleanPhone = userPhone.filter { it.isDigit() }.let { if (it.length >= 10) it.takeLast(10) else "9999999999" }
        val productinfo = (if (goalTitle.isNotBlank()) "Vault $goalTitle" else "Our Bloom Vault").replace(Regex("[^a-zA-Z0-9 ]"), "").trim().take(50).ifBlank { "Our Bloom Vault" }
        val cleanNote = note.replace(Regex("[|\n\r]"), " ").trim().take(100)

        val surl = "https://our-bloom.onrender.com/api/payu/success"
        val furl = "https://our-bloom.onrender.com/api/payu/failure"

        // Hash Formula: key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT
        val hashString = "$payuKey|$txnid|$formattedAmount|$productinfo|$cleanFirstName|$cleanEmail|$coupleId|$userId|$cleanFirstName|$goalId|$cleanNote||||||$payuSalt"
        val digest = MessageDigest.getInstance("SHA-512")
        val hashBytes = digest.digest(hashString.toByteArray(Charsets.UTF_8))
        val hash = hashBytes.joinToString("") { "%02x".format(it) }

        val postData = StringBuilder().apply {
            append("key=").append(URLEncoder.encode(payuKey, "UTF-8"))
            append("&txnid=").append(URLEncoder.encode(txnid, "UTF-8"))
            append("&amount=").append(URLEncoder.encode(formattedAmount, "UTF-8"))
            append("&productinfo=").append(URLEncoder.encode(productinfo, "UTF-8"))
            append("&firstname=").append(URLEncoder.encode(cleanFirstName, "UTF-8"))
            append("&email=").append(URLEncoder.encode(cleanEmail, "UTF-8"))
            append("&phone=").append(URLEncoder.encode(cleanPhone, "UTF-8"))
            append("&surl=").append(URLEncoder.encode(surl, "UTF-8"))
            append("&furl=").append(URLEncoder.encode(furl, "UTF-8"))
            append("&hash=").append(URLEncoder.encode(hash, "UTF-8"))
            append("&udf1=").append(URLEncoder.encode(coupleId, "UTF-8"))
            append("&udf2=").append(URLEncoder.encode(userId, "UTF-8"))
            append("&udf3=").append(URLEncoder.encode(cleanFirstName, "UTF-8"))
            append("&udf4=").append(URLEncoder.encode(goalId, "UTF-8"))
            append("&udf5=").append(URLEncoder.encode(cleanNote, "UTF-8"))
        }.toString().toByteArray(Charsets.UTF_8)

        Log.d("PayUCheckout", "Initiating Live PayU Checkout at $actionUrl for amount $formattedAmount (desktopMode=$isDesktopMode)")
        webView.postUrl(actionUrl, postData)
    }

    private fun onPaymentApproved(ref: String? = null) {
        if (isCompleted) return
        isCompleted = true

        progressBar.visibility = View.VISIBLE
        Toast.makeText(this, "Payment received. Vault credit is pending verification.", Toast.LENGTH_LONG).show()

        val generatedRef = ref?.ifBlank { null } ?: currentTxnId.ifBlank { "PAYU_${System.currentTimeMillis()}" }

        val resultIntent = Intent().apply {
            putExtra("txnid", currentTxnId)
            putExtra("reference", generatedRef)
            putExtra("amount", amount)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun onPaymentDeclined() {
        if (isCompleted) return
        isCompleted = true
        Toast.makeText(this, "Payment was not completed. No money was deducted.", Toast.LENGTH_LONG).show()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onResume() {
        super.onResume()
        try {
            webView.onResume()
        } catch (_: Exception) {}
    }

    override fun onPause() {
        try {
            webView.onPause()
        } catch (_: Exception) {}
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Cancel Payment?")
                .setMessage("Are you sure you want to cancel this payment? No money will be deducted from your account.")
                .setPositiveButton("Yes, Cancel") { _, _ ->
                    onPaymentDeclined()
                }
                .setNegativeButton("Continue Payment", null)
                .show()
        }
    }

    override fun onDestroy() {
        try {
            webView.stopLoading()
            webView.destroy()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
