package com.ourbloom.admin.wallets

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
import com.ourbloom.admin.data.models.SavingsWallet
import java.util.Locale

class AdjustBalanceDialog(
    context: Context,
    private val wallet: SavingsWallet,
    private val onConfirm: (newBalance: Double, reason: String) -> Unit
) : Dialog(context) {

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_adjust_wallet)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val cleanBal = if (wallet.totalBalance % 1.0 == 0.0) wallet.totalBalance.toInt().toString() else String.format(Locale.US, "%.2f", wallet.totalBalance)
        findViewById<TextView>(R.id.tv_adjust_current_balance).text = "Current Holding: ₹$cleanBal"

        val etNewBalance = findViewById<EditText>(R.id.et_adjust_new_balance)
        val etReason = findViewById<EditText>(R.id.et_adjust_reason)

        findViewById<Button>(R.id.btn_cancel_adjust).setOnClickListener {
            dismiss()
        }

        findViewById<Button>(R.id.btn_confirm_adjust).setOnClickListener {
            val newBalStr = etNewBalance.text.toString().trim()
            val reason = etReason.text.toString().trim()

            val newBal = newBalStr.toDoubleOrNull()
            if (newBal == null || newBal < 0.0) {
                Toast.makeText(context, "Please enter a valid non-negative balance", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (reason.isBlank()) {
                Toast.makeText(context, "Audit reason is required for any balance adjustment", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            onConfirm(newBal, reason)
            dismiss()
        }
    }
}
