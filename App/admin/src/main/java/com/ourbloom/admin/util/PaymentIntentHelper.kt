package com.ourbloom.admin.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder
import java.util.Locale

object PaymentIntentHelper {

    fun launchUpiPayment(
        context: Context,
        upiId: String,
        payeeName: String,
        amount: Double,
        note: String
    ): Boolean {
        if (upiId.isBlank()) {
            Toast.makeText(context, "No UPI ID provided for this payout", Toast.LENGTH_SHORT).show()
            return false
        }

        try {
            val formattedAmount = String.format(Locale.US, "%.2f", amount)
            val cleanName = URLEncoder.encode(payeeName.ifBlank { "Partner" }, "UTF-8")
            val cleanNote = URLEncoder.encode(note.ifBlank { "Our Bloom Payout" }, "UTF-8")
            val cleanVpa = upiId.trim()

            val upiUriString = "upi://pay?pa=$cleanVpa&pn=$cleanName&am=$formattedAmount&cu=INR&tn=$cleanNote"
            val uri = Uri.parse(upiUriString)

            val intent = Intent(Intent.ACTION_VIEW, uri)
            val chooser = Intent.createChooser(intent, "Pay ₹$formattedAmount via UPI App")
            context.startActivity(chooser)
            return true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open UPI app: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
    }
}
