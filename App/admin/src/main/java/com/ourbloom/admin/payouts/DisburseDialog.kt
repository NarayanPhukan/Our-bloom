package com.ourbloom.admin.payouts

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.ourbloom.admin.R
import com.ourbloom.admin.data.models.WithdrawalRequest
import java.util.Locale

class DisburseDialog(
    context: Context,
    private val request: WithdrawalRequest,
    private val onConfirm: (utrNumber: String, notes: String) -> Unit
) : Dialog(context) {

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_disburse_payout)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val cleanAmount = if (request.amount % 1.0 == 0.0) request.amount.toInt().toString() else String.format(Locale.US, "%.2f", request.amount)
        val tvSubtitle = findViewById<TextView>(R.id.tv_dialog_disburse_subtitle)
        tvSubtitle.text = "Disbursing ₹$cleanAmount to ${request.requestedByName.ifBlank { "Partner" }}"

        val bankDetails = request.jointAccount ?: request.partner1Account
        val accInfo = StringBuilder()
        if (bankDetails?.accountNumber?.isNotBlank() == true) {
            accInfo.append("A/C: ${bankDetails.accountNumber} (${bankDetails.ifscCode})")
        }
        if (bankDetails?.upiId?.isNotBlank() == true) {
            if (accInfo.isNotEmpty()) accInfo.append(" • ")
            accInfo.append("UPI: ${bankDetails.upiId}")
        }
        findViewById<TextView>(R.id.tv_dialog_disburse_account)?.text = accInfo.toString().ifBlank { "Destination: Mutual Couple Bank" }

        val etUtr = findViewById<EditText>(R.id.et_disburse_utr)
        val etNotes = findViewById<EditText>(R.id.et_disburse_notes)

        findViewById<Button>(R.id.btn_cancel_disburse).setOnClickListener {
            dismiss()
        }

        findViewById<Button>(R.id.btn_confirm_disburse).setOnClickListener {
            val utr = etUtr.text.toString().trim()
            if (utr.isBlank()) {
                Toast.makeText(context, "Please enter the Bank UTR / IMPS reference number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val notes = etNotes.text.toString().trim()
            onConfirm(utr, notes)
            dismiss()
        }
    }
}
