package com.ourbloom.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.ourbloom.app.BuildConfig
import java.net.URLEncoder
import java.util.Locale

object SavingsPaymentHelper {

    val ACCOUNT_NUMBER: String
        get() = BuildConfig.VAULT_ACCOUNT_NUMBER
    val IFSC_CODE: String
        get() = BuildConfig.VAULT_IFSC_CODE
    val BANK_NAME: String
        get() = BuildConfig.VAULT_BANK_NAME
    const val RECEIVER_DISPLAY_NAME = "Our Bloom Vault"
    val UPI_VPA: String
        get() = BuildConfig.VAULT_UPI_VPA

    data class UpiResult(
        val isSuccess: Boolean,
        val utr: String,
        val txnId: String,
        val rawResponse: String
    )

    fun buildUpiUri(amount: Double, note: String = ""): Uri {
        val formattedAmount = String.format(Locale.US, "%.2f", amount)
        val cleanNote = note.trim().ifBlank { "Our Bloom Savings" }
        return Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", UPI_VPA)
            .appendQueryParameter("pn", RECEIVER_DISPLAY_NAME)
            .appendQueryParameter("am", formattedAmount)
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", cleanNote)
            .build()
    }

    fun buildUpiIntent(amount: Double, note: String = ""): Intent {
        val uri = buildUpiUri(amount, note)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        return Intent.createChooser(intent, "Pay with UPI")
    }

    fun parseUpiResponse(rawResponse: String?): UpiResult {
        if (rawResponse.isNullOrBlank()) {
            return UpiResult(isSuccess = false, utr = "", txnId = "", rawResponse = "")
        }

        val map = mutableMapOf<String, String>()
        val pairs = rawResponse.split("&")
        for (pair in pairs) {
            val parts = pair.split("=")
            if (parts.size == 2) {
                map[parts[0].trim().lowercase(Locale.US)] = parts[1].trim()
            }
        }

        val status = map["status"]?.lowercase(Locale.US) ?: ""
        val isSuccess = status == "success" || status == "submitted"

        val utr = map["approvalrefno"]
            ?: map["txnref"]
            ?: map["refid"]
            ?: map["banktxnid"]
            ?: ""

        val txnId = map["txnid"] ?: ""

        return UpiResult(
            isSuccess = isSuccess,
            utr = utr,
            txnId = txnId,
            rawResponse = rawResponse
        )
    }

    fun getUpiQrUrl(amount: Double, note: String = ""): String {
        return try {
            val uri = buildUpiUri(amount, note).toString()
            val encoded = URLEncoder.encode(uri, "UTF-8")
            "https://api.qrserver.com/v1/create-qr-code/?size=350x350&data=$encoded"
        } catch (_: Exception) {
            ""
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard ✓", Toast.LENGTH_SHORT).show()
    }
}
