package com.ourbloom.admin.transactions

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.ourbloom.admin.R

class ManualCreditDialog(
    context: Context,
    private val onConfirm: (coupleId: String, amount: Double, utr: String, note: String) -> Unit
) : Dialog(context) {

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_manual_credit)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etCoupleId = findViewById<EditText>(R.id.et_credit_couple_id)
        val etAmount = findViewById<EditText>(R.id.et_credit_amount)
        val etUtr = findViewById<EditText>(R.id.et_credit_utr)
        val etNote = findViewById<EditText>(R.id.et_credit_note)

        findViewById<Button>(R.id.btn_cancel_credit).setOnClickListener {
            dismiss()
        }

        findViewById<Button>(R.id.btn_confirm_credit).setOnClickListener {
            val coupleId = etCoupleId.text.toString().trim()
            val amountStr = etAmount.text.toString().trim()

            if (coupleId.isBlank()) {
                Toast.makeText(context, "Please enter the Couple ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0.0) {
                Toast.makeText(context, "Please enter a valid positive amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val utr = etUtr.text.toString().trim()
            val note = etNote.text.toString().trim()

            onConfirm(coupleId, amount, utr, note)
            dismiss()
        }
    }
}
