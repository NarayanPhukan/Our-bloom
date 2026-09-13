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

class RejectRefundDialog(
    context: Context,
    private val request: WithdrawalRequest,
    private val onConfirm: (reason: String) -> Unit
) : Dialog(context) {

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_reject_payout)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val cleanAmount = if (request.amount % 1.0 == 0.0) request.amount.toInt().toString() else String.format(Locale.US, "%.2f", request.amount)
        val tvSubtitle = findViewById<TextView>(R.id.tv_dialog_reject_subtitle)
        tvSubtitle.text = "Rejecting request. ₹$cleanAmount will be restored to couple's vault immediately."

        val etReason = findViewById<EditText>(R.id.et_reject_reason)

        findViewById<Button>(R.id.btn_cancel_reject).setOnClickListener {
            dismiss()
        }

        findViewById<Button>(R.id.btn_confirm_reject).setOnClickListener {
            val reason = etReason.text.toString().trim()
            if (reason.isBlank()) {
                Toast.makeText(context, "Please enter a reason for rejection", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onConfirm(reason)
            dismiss()
        }
    }
}
