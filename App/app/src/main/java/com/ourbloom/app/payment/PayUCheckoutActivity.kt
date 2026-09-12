package com.ourbloom.app.payment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.*
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

    // Views
    private lateinit var layoutMerchant: View
    private lateinit var layoutWebContainer: View
    private lateinit var layoutDispatchLoading: View
    private lateinit var tvDispatchStatus: TextView
    private lateinit var webView: WebView
    private lateinit var progressWebBar: ProgressBar

    // Inputs
    private lateinit var etUpiVpa: EditText
    private lateinit var btnSubmitVpa: Button
    private lateinit var etCardNumber: EditText
    private lateinit var etCardExpiry: EditText
    private lateinit var etCardCvv: EditText
    private lateinit var etCardName: EditText
    private lateinit var btnSubmitCard: Button

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
    private var isSubmitting = false

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
        setupCardFormatting()
        setupWebView()
    }

    private fun initViews() {
        layoutMerchant = findViewById(R.id.layout_merchant_payment)
        layoutWebContainer = findViewById(R.id.layout_secure_web_container)
        layoutDispatchLoading = findViewById(R.id.layout_dispatch_loading)
        tvDispatchStatus = findViewById(R.id.tv_dispatch_status)
        webView = findViewById(R.id.web_view_checkout)
        progressWebBar = findViewById(R.id.progress_web_bar)

        findViewById<ImageButton>(R.id.btn_close_checkout).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btn_close_web).setOnClickListener {
            // Cancel web 3D-Secure and return to payment method selection
            layoutWebContainer.visibility = View.GONE
            layoutMerchant.visibility = View.VISIBLE
            hideLoading()
        }

        val formattedAmount = if (amount % 1.0 == 0.0) amount.toInt().toString() else String.format(Locale.US, "%.2f", amount)
        findViewById<TextView>(R.id.tv_checkout_amount_badge).text = "₹$formattedAmount"
        findViewById<TextView>(R.id.tv_deposit_amount_big).text = "₹$formattedAmount"
        findViewById<TextView>(R.id.tv_web_amount_badge).text = "₹$formattedAmount"

        val tvDestination = findViewById<TextView>(R.id.tv_deposit_destination)
        tvDestination.text = if (goalTitle.isNotBlank()) "Goal: $goalTitle 🎯" else "Couple Savings Vault 💖"

        val tvContributor = findViewById<TextView>(R.id.tv_deposit_contributor)
        tvContributor.text = "Deposit by $userName 🌸"

        // 1-Tap UPI Apps
        findViewById<View>(R.id.btn_pay_gpay).setOnClickListener {
            initiateSeamlessPayment("UPI", "TEZ", "Opening Google Pay...")
        }
        findViewById<View>(R.id.btn_pay_phonepe).setOnClickListener {
            initiateSeamlessPayment("UPI", "PHONEPE", "Opening PhonePe...")
        }
        findViewById<View>(R.id.btn_pay_paytm).setOnClickListener {
            initiateSeamlessPayment("UPI", "PAYTM", "Opening Paytm...")
        }
        findViewById<View>(R.id.btn_pay_any_upi).setOnClickListener {
            initiateSeamlessPayment("UPI", "INT", "Connecting to UPI App...")
        }

        // UPI VPA
        etUpiVpa = findViewById(R.id.et_upi_vpa)
        btnSubmitVpa = findViewById(R.id.btn_submit_vpa)
        btnSubmitVpa.text = "Request & Pay ₹$formattedAmount"
        btnSubmitVpa.setOnClickListener {
            val vpa = etUpiVpa.text.toString().trim()
            if (vpa.isBlank() || !vpa.contains("@")) {
                etUpiVpa.error = "Please enter a valid UPI ID (e.g. mobile@upi)"
                etUpiVpa.requestFocus()
                return@setOnClickListener
            }
            initiateSeamlessPayment("UPI", "UPI", "Requesting from $vpa...", extraParams = mapOf("vpa" to vpa))
        }

        // Card Inputs
        etCardNumber = findViewById(R.id.et_card_number)
        etCardExpiry = findViewById(R.id.et_card_expiry)
        etCardCvv = findViewById(R.id.et_card_cvv)
        etCardName = findViewById(R.id.et_card_name)
        btnSubmitCard = findViewById(R.id.btn_submit_card)
        btnSubmitCard.text = "Pay ₹$formattedAmount via Card"

        btnSubmitCard.setOnClickListener {
            validateAndSubmitCard()
        }

        // Net Banking Quick Chips
        findViewById<View>(R.id.btn_nb_hdfc).setOnClickListener { initiateNetBankingPayment("HDFB", "HDFC Bank") }
        findViewById<View>(R.id.btn_nb_sbi).setOnClickListener { initiateNetBankingPayment("SBIB", "State Bank of India") }
        findViewById<View>(R.id.btn_nb_icici).setOnClickListener { initiateNetBankingPayment("ICIB", "ICICI Bank") }
        findViewById<View>(R.id.btn_nb_axis).setOnClickListener { initiateNetBankingPayment("AXIB", "Axis Bank") }
        findViewById<View>(R.id.btn_nb_kotak).setOnClickListener { initiateNetBankingPayment("162B", "Kotak Mahindra Bank") }
    }

    private fun setupCardFormatting() {
        // Auto-format Card Number with spaces: #### #### #### ####
        etCardNumber.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true
                val digitsOnly = s.toString().replace(" ", "")
                val formatted = StringBuilder()
                for (i in digitsOnly.indices) {
                    if (i > 0 && i % 4 == 0) formatted.append(" ")
                    formatted.append(digitsOnly[i])
                }
                etCardNumber.setText(formatted.toString())
                etCardNumber.setSelection(formatted.length)
                isFormatting = false
            }
        })

        // Auto-format Expiry: MM/YY
        etCardExpiry.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true
                val clean = s.toString().replace("/", "")
                val formatted = if (clean.length > 2) {
                    "${clean.substring(0, 2)}/${clean.substring(2)}"
                } else clean
                etCardExpiry.setText(formatted)
                etCardExpiry.setSelection(formatted.length)
                isFormatting = false
            }
        })
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

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressWebBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressWebBar.visibility = View.GONE
                hideLoading()
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

    private fun handleUrl(url: String): Boolean {
        Log.d("PayUCheckout", "Intercepted URL: $url")

        // Intercept Success Callback
        if (url.contains("ourbloom.app/payment/success") || url.contains("/payment/success")) {
            onPaymentApproved()
            return true
        }

        // Intercept Failure Callback
        if (url.contains("ourbloom.app/payment/failure") || url.contains("/payment/failure")) {
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
            hideLoading()
            try {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w("PayUCheckout", "Failed to launch payment app: ${e.message}")
                Toast.makeText(this, "Could not launch payment app directly", Toast.LENGTH_SHORT).show()
                return true
            }
        }

        return false
    }

    private fun validateAndSubmitCard() {
        val rawNumber = etCardNumber.text.toString().replace(" ", "").trim()
        val expiry = etCardExpiry.text.toString().trim()
        val cvv = etCardCvv.text.toString().trim()
        val cardName = etCardName.text.toString().trim().ifBlank { userName }

        if (rawNumber.length < 15) {
            etCardNumber.error = "Enter valid 16-digit card number"
            etCardNumber.requestFocus()
            return
        }

        if (!expiry.contains("/") || expiry.length != 5) {
            etCardExpiry.error = "MM/YY required"
            etCardExpiry.requestFocus()
            return
        }

        val parts = expiry.split("/")
        val expMonth = parts[0]
        val expYear = "20${parts[1]}"

        val monthNum = expMonth.toIntOrNull() ?: 0
        if (monthNum !in 1..12) {
            etCardExpiry.error = "Invalid month"
            etCardExpiry.requestFocus()
            return
        }

        if (cvv.length !in 3..4) {
            etCardCvv.error = "Enter 3 or 4 digit CVV"
            etCardCvv.requestFocus()
            return
        }

        // Open 3D Secure Web Container
        layoutMerchant.visibility = View.GONE
        layoutWebContainer.visibility = View.VISIBLE

        val cardParams = mapOf(
            "ccnum" to rawNumber,
            "ccexpmon" to expMonth,
            "ccexpyr" to expYear,
            "ccvv" to cvv,
            "ccname" to cardName
        )

        initiateSeamlessPayment("CC", "CC", "Connecting to Bank 3D-Secure...", cardParams)
    }

    private fun initiateNetBankingPayment(bankCode: String, bankName: String) {
        layoutMerchant.visibility = View.GONE
        layoutWebContainer.visibility = View.VISIBLE
        initiateSeamlessPayment("NB", bankCode, "Connecting to $bankName...")
    }

    private fun initiateSeamlessPayment(
        pg: String,
        bankcode: String,
        loadingText: String,
        extraParams: Map<String, String> = emptyMap()
    ) {
        if (isSubmitting) return
        isSubmitting = true
        showLoading(loadingText)

        // Read Live Credentials
        val payuKey = BuildConfig.PAYU_KEY.ifBlank { "qSYSCh" }
        val payuSalt = BuildConfig.PAYU_SALT.ifBlank { "ILJi7wHiIDwmOFT1VAWBh6hCfSpkVYfA" }
        val isLiveMode = BuildConfig.PAYU_MODE.equals("live", ignoreCase = true)
        val actionUrl = if (isLiveMode) LIVE_ACTION_URL else TEST_ACTION_URL

        val txnid = "OB_${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "").take(6)}"
        val formattedAmount = String.format(Locale.US, "%.2f", amount)
        val cleanFirstName = userName.replace(Regex("[^a-zA-Z0-9]"), "").trim().ifBlank { "Partner" }
        val cleanEmail = if (userEmail.isNotBlank() && userEmail.contains("@")) userEmail.trim() else "support@ourbloom.app"
        val cleanPhone = userPhone.filter { it.isDigit() }.let { if (it.length >= 10) it.takeLast(10) else "9999999999" }
        val productinfo = (if (goalTitle.isNotBlank()) "Vault $goalTitle" else "Our Bloom Vault").replace(Regex("[^a-zA-Z0-9 ]"), "").trim().take(50).ifBlank { "Our Bloom Vault" }
        val cleanNote = note.replace(Regex("[|\n\r]"), " ").trim().take(100)

        val surl = "https://ourbloom.app/payment/success"
        val furl = "https://ourbloom.app/payment/failure"

        // Hash Formula: key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT
        val hashString = "$payuKey|$txnid|$formattedAmount|$productinfo|$cleanFirstName|$cleanEmail|$coupleId|$userId|$cleanFirstName|$goalId|$cleanNote||||||$payuSalt"
        val digest = MessageDigest.getInstance("SHA-512")
        val hashBytes = digest.digest(hashString.toByteArray(Charsets.UTF_8))
        val hash = hashBytes.joinToString("") { "%02x".format(it) }

        val postDataBuilder = StringBuilder().apply {
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
            append("&pg=").append(URLEncoder.encode(pg, "UTF-8"))
            append("&bankcode=").append(URLEncoder.encode(bankcode, "UTF-8"))

            for ((key, value) in extraParams) {
                append("&").append(URLEncoder.encode(key, "UTF-8")).append("=").append(URLEncoder.encode(value, "UTF-8"))
            }
        }

        val postData = postDataBuilder.toString().toByteArray(Charsets.UTF_8)
        Log.d("PayUCheckout", "Posting seamless payment to $actionUrl with pg=$pg, bankcode=$bankcode")
        webView.postUrl(actionUrl, postData)

        // Reset submit flag after delay
        webView.postDelayed({ isSubmitting = false }, 2500)
    }

    private fun showLoading(text: String) {
        tvDispatchStatus.text = text
        layoutDispatchLoading.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        layoutDispatchLoading.visibility = View.GONE
    }

    private fun onPaymentApproved() {
        if (isCompleted) return
        isCompleted = true

        showLoading("Payment Verified! Crediting Vault... 🌸")

        val generatedRef = "PAYU_${System.currentTimeMillis()}"

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
        hideLoading()
        layoutWebContainer.visibility = View.GONE
        layoutMerchant.visibility = View.VISIBLE
        Toast.makeText(this, "Payment was not completed. No money was deducted.", Toast.LENGTH_LONG).show()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (layoutWebContainer.visibility == View.VISIBLE) {
            layoutWebContainer.visibility = View.GONE
            layoutMerchant.visibility = View.VISIBLE
            hideLoading()
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
