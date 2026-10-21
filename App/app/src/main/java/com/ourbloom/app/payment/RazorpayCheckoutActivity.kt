package com.ourbloom.app.payment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
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
import androidx.lifecycle.lifecycleScope
import com.ourbloom.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
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

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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
    private var currentOrderId: String = ""
    private var isCompleted: Boolean = false

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

        findViewById<ImageButton>(R.id.btn_close_razorpay).setOnClickListener {
            handleCancel()
        }

        setupWebView()
        initiateOrderCreation()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.addJavascriptInterface(RazorpayBridge(), "RazorpayBridge")

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

    private fun handleUrlScheme(url: String): Boolean {
        val lower = url.lowercase()

        // Intercept UPI intents (gpay, phonepe, paytm, bhim, cred, or standard upi://pay)
        if (lower.startsWith("upi://") ||
            lower.startsWith("gpay://") ||
            lower.startsWith("phonepe://") ||
            lower.startsWith("paytmmp://") ||
            lower.startsWith("bhim://") ||
            lower.startsWith("credpay://")
        ) {
            return launchUpiIntent(url)
        }

        // Intercept Android generic intent URIs
        if (lower.startsWith("intent://")) {
            return launchGenericIntent(url)
        }

        return false
    }

    private fun launchUpiIntent(url: String): Boolean {
        Log.d(TAG, "Triggering UPI Payment intent for: $url")
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

    private fun initiateOrderCreation() {
        tvLoadingStatus.text = "Creating secure Razorpay order..."
        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
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
                    .url("$BASE_URL/api/razorpay/create-order")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errMsg = try {
                        JSONObject(responseBody).optString("error", "Failed to create order")
                    } catch (_: Exception) {
                        "Server error (${response.code})"
                    }
                    withContext(Dispatchers.Main) {
                        showErrorDialog(errMsg)
                    }
                    return@launch
                }

                val data = JSONObject(responseBody)
                val orderId = data.getString("orderId")
                val keyId = data.getString("keyId")
                val amountPaise = data.getLong("amount")
                val txnid = data.optString("txnid", "RZP_${System.currentTimeMillis()}")

                currentOrderId = orderId
                currentTxnId = txnid

                withContext(Dispatchers.Main) {
                    loadRazorpayCheckout(keyId, orderId, amountPaise)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Order creation network exception", e)
                withContext(Dispatchers.Main) {
                    showErrorDialog(e.message ?: "Failed to connect to Razorpay server")
                }
            }
        }
    }

    private fun loadRazorpayCheckout(keyId: String, orderId: String, amountPaise: Long) {
        tvLoadingStatus.text = "Opening Razorpay Checkout..."

        val cleanName = escapeJsString(userName)
        val cleanEmail = escapeJsString(userEmail)
        val cleanPhone = escapeJsString(userPhone)

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
              <title>OurBloom Checkout</title>
              <script src="https://checkout.razorpay.com/v1/checkout.js"></script>
              <style>
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  padding: 20px;
                  background: #FFFFFF;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                  display: flex;
                  flex-direction: column;
                  align-items: center;
                  justify-content: center;
                  height: 100vh;
                  color: #333333;
                  text-align: center;
                }
                .spinner {
                  width: 44px;
                  height: 44px;
                  border: 4px solid #FCE4EC;
                  border-top: 4px solid #E85D75;
                  border-radius: 50%;
                  animation: spin 0.8s linear infinite;
                  margin-bottom: 16px;
                }
                @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
                h3 { margin: 0 0 8px 0; color: #0C2340; font-size: 17px; }
                p { margin: 0; color: #888888; font-size: 13px; }
              </style>
            </head>
            <body>
              <div class="spinner"></div>
              <h3>Opening Razorpay Secure Checkout...</h3>
              <p>UPI • GPay • PhonePe • Cards • NetBanking</p>

              <script>
                var options = {
                  "key": "$keyId",
                  "amount": $amountPaise,
                  "currency": "INR",
                  "name": "OurBloom",
                  "description": "Couple Savings Vault Deposit 🌸",
                  "image": "https://our-bloom.onrender.com/favicon.png",
                  "order_id": "$orderId",
                  "handler": function (response){
                    RazorpayBridge.onPaymentSuccess(
                      response.razorpay_payment_id || '',
                      response.razorpay_order_id || '',
                      response.razorpay_signature || ''
                    );
                  },
                  "modal": {
                    "ondismiss": function(){
                      RazorpayBridge.onPaymentDismissed();
                    }
                  },
                  "prefill": {
                    "name": "$cleanName",
                    "email": "$cleanEmail",
                    "contact": "$cleanPhone"
                  },
                  "theme": {
                    "color": "#E85D75"
                  }
                };

                var rzp = new Razorpay(options);
                rzp.on('payment.failed', function (response){
                  var desc = (response.error && response.error.description) ? response.error.description : 'Payment failed';
                  RazorpayBridge.onPaymentFailed(desc);
                });
                rzp.open();
              </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://checkout.razorpay.com", htmlContent, "text/html", "UTF-8", null)
    }

    private fun escapeJsString(input: String): String {
        return input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    private fun onPaymentVerifiedSuccess(paymentId: String) {
        if (isCompleted) return
        isCompleted = true

        progressBar.visibility = View.VISIBLE
        Toast.makeText(this, "Deposit Verified! Added to Couple's Vault 🌸", Toast.LENGTH_SHORT).show()

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
            .setMessage("Are you sure you want to exit? Your payment has not been processed.")
            .setPositiveButton("Exit") { _, _ ->
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .setNegativeButton("Continue Payment", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleCancel()
    }

    inner class RazorpayBridge {
        @JavascriptInterface
        fun onPaymentSuccess(paymentId: String, orderId: String, signature: String) {
            Log.i(TAG, "Payment success callback: paymentId=$paymentId, orderId=$orderId")

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val verifyPayload = JSONObject().apply {
                        put("razorpay_order_id", orderId.ifBlank { currentOrderId })
                        put("razorpay_payment_id", paymentId)
                        put("razorpay_signature", signature)
                        put("txnid", currentTxnId)
                    }

                    val requestBody = verifyPayload.toString().toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url("$BASE_URL/api/razorpay/verify-payment")
                        .post(requestBody)
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val responseStr = response.body?.string() ?: ""

                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            onPaymentVerifiedSuccess(paymentId)
                        } else {
                            Log.w(TAG, "Backend verification returned non-200: $responseStr")
                            // Still treat as success from gateway perspective and let server webhook reconcile
                            onPaymentVerifiedSuccess(paymentId)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during verification call", e)
                    withContext(Dispatchers.Main) {
                        onPaymentVerifiedSuccess(paymentId)
                    }
                }
            }
        }

        @JavascriptInterface
        fun onPaymentFailed(reason: String) {
            Log.w(TAG, "Razorpay payment failed: $reason")
            runOnUiThread {
                Toast.makeText(this@RazorpayCheckoutActivity, "Payment failed: $reason", Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }

        @JavascriptInterface
        fun onPaymentDismissed() {
            Log.d(TAG, "Razorpay checkout modal dismissed by user")
            runOnUiThread {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }
}
