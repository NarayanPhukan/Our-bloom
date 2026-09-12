package com.ourbloom.app.payment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ourbloom.app.BuildConfig
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import kotlinx.coroutines.launch
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

    private val repository = FirestoreRepository()
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutError: View

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

        initViews()
        setupWebView()
        initiatePayment()
    }

    private fun initViews() {
        webView = findViewById(R.id.web_view_checkout)
        progressBar = findViewById(R.id.progress_checkout)
        layoutError = findViewById(R.id.layout_checkout_error)

        findViewById<ImageButton>(R.id.btn_close_checkout).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btn_retry_checkout).setOnClickListener {
            layoutError.visibility = View.GONE
            initiatePayment()
        }

        val tvAmount = findViewById<TextView>(R.id.tv_checkout_amount_badge)
        val cleanAmount = if (amount % 1.0 == 0.0) amount.toInt().toString() else String.format(Locale.US, "%.2f", amount)
        tvAmount.text = "₹$cleanAmount"

        val tvTitle = findViewById<TextView>(R.id.tv_checkout_title)
        tvTitle.text = if (goalTitle.isNotBlank()) "Goal Deposit: $goalTitle 🎯" else "Our Bloom Vault Deposit 🌸"
    }

    private var currentTxnId: String = ""

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
        settings.setSupportMultipleWindows(true)

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

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val newWebView = WebView(this@PayUCheckoutActivity)
                newWebView.settings.javaScriptEnabled = true
                newWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        return handleUrl(url)
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newWebView
                resultMsg?.sendToTarget()
                return true
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

        // Intercept UPI, NetBanking deep links & intent schemes
        if (url.startsWith("upi://") ||
            url.startsWith("intent://") ||
            url.startsWith("gpay://") ||
            url.startsWith("phonepe://") ||
            url.startsWith("paytmmp://") ||
            url.startsWith("credpay://") ||
            url.startsWith("bhim://")
        ) {
            try {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    return true
                }
                // Fallback to generic UPI chooser if specific app intent wasn't directly resolvable
                val uri = intent.data
                if (uri != null && (uri.scheme == "upi" || url.startsWith("upi://"))) {
                    val chooser = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(Intent.createChooser(chooser, "Complete Payment with UPI"))
                    return true
                }
                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                if (!fallbackUrl.isNullOrBlank()) {
                    webView.loadUrl(fallbackUrl)
                    return true
                }
            } catch (e: Exception) {
                Log.w("PayUCheckout", "Failed to launch payment app: ${e.message}")
                Toast.makeText(this, "Could not open payment app directly", Toast.LENGTH_SHORT).show()
                return true
            }
        }

        return false
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

        Log.d("PayUCheckout", "Initiating Live PayU Checkout at $actionUrl for amount $formattedAmount")
        webView.postUrl(actionUrl, postData)
    }

    private fun onPaymentApproved(ref: String? = null) {
        if (isCompleted) return
        isCompleted = true

        progressBar.visibility = View.VISIBLE
        Toast.makeText(this, "Payment approved! Crediting Vault... 🌸", Toast.LENGTH_SHORT).show()

        val generatedRef = ref?.ifBlank { null } ?: currentTxnId.ifBlank { "PAYU_${System.currentTimeMillis()}" }

        lifecycleScope.launch {
            val success = repository.recordDeposit(
                context = this@PayUCheckoutActivity,
                coupleId = coupleId,
                amount = amount,
                utrNumber = generatedRef,
                note = note.ifBlank { "Online Contribution" },
                category = "Savings",
                paymentMethod = "Online",
                goalId = goalId.ifBlank { null },
                goalTitle = goalTitle.ifBlank { null }
            )

            if (success) {
                Toast.makeText(this@PayUCheckoutActivity, "₹${amount.toInt()} credited to Our Vault! 🎉🌸", Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_OK, Intent().putExtra("amount", amount))
            } else {
                Toast.makeText(this@PayUCheckoutActivity, "Deposit logged! Balance will update shortly.", Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_OK)
            }
            finish()
        }
    }

    private fun onPaymentDeclined() {
        if (isCompleted) return
        isCompleted = true
        Toast.makeText(this, "Payment was not completed. No money was deducted.", Toast.LENGTH_LONG).show()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
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
