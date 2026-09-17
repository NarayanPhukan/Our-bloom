package com.ourbloom.admin.broadcast

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.ourbloom.admin.R
import com.ourbloom.admin.data.AdminFirestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BroadcastDialog(
    context: Context,
    private val defaultCoupleId: String? = null,
    private val repository: AdminFirestoreRepository,
    private val coroutineScope: CoroutineScope
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_broadcast)

        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        initViews()
    }

    private fun initViews() {
        val btnClose = findViewById<ImageButton>(R.id.btn_close_broadcast)
        val rgTarget = findViewById<RadioGroup>(R.id.rg_broadcast_target)
        val rbTargetCouple = findViewById<RadioButton>(R.id.rb_target_couple)
        val rbTargetAll = findViewById<RadioButton>(R.id.rb_target_all)
        val layoutCoupleId = findViewById<LinearLayout>(R.id.layout_broadcast_couple_id)
        val etCoupleId = findViewById<EditText>(R.id.et_broadcast_couple_id)
        val etTitle = findViewById<EditText>(R.id.et_broadcast_title)
        val etBody = findViewById<EditText>(R.id.et_broadcast_body)
        val btnCancel = findViewById<MaterialButton>(R.id.btn_cancel_broadcast)
        val btnSend = findViewById<MaterialButton>(R.id.btn_send_broadcast)

        btnClose?.setOnClickListener { dismiss() }
        btnCancel?.setOnClickListener { dismiss() }

        if (!defaultCoupleId.isNullOrBlank()) {
            rbTargetCouple?.isChecked = true
            layoutCoupleId?.visibility = View.VISIBLE
            etCoupleId?.setText(defaultCoupleId)
        }

        rgTarget?.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_target_couple) {
                layoutCoupleId?.visibility = View.VISIBLE
            } else {
                layoutCoupleId?.visibility = View.GONE
            }
        }

        btnSend?.setOnClickListener {
            val title = etTitle?.text?.toString()?.trim() ?: ""
            val body = etBody?.text?.toString()?.trim() ?: ""
            val isCoupleTarget = rgTarget?.checkedRadioButtonId == R.id.rb_target_couple
            val coupleId = etCoupleId?.text?.toString()?.trim() ?: ""

            if (title.isBlank()) {
                etTitle?.error = "Title required"
                return@setOnClickListener
            }
            if (body.isBlank()) {
                etBody?.error = "Message body required"
                return@setOnClickListener
            }
            if (isCoupleTarget && coupleId.isBlank()) {
                etCoupleId?.error = "Couple ID required"
                return@setOnClickListener
            }

            val targetDesc = if (isCoupleTarget) "Couple $coupleId" else "ALL active couples"

            AlertDialog.Builder(context)
                .setTitle("Confirm Broadcast")
                .setMessage("Are you sure you want to send this push notification to $targetDesc?\n\n\"$title\"")
                .setPositiveButton("Dispatch") { _, _ ->
                    btnSend.isEnabled = false
                    btnSend.text = "Sending..."

                    coroutineScope.launch {
                        val result = repository.sendBroadcastAnnouncement(
                            title = title,
                            body = body,
                            target = if (isCoupleTarget) "couple" else "all",
                            coupleId = if (isCoupleTarget) coupleId else ""
                        )
                        withContext(Dispatchers.Main) {
                            btnSend.isEnabled = true
                            btnSend.text = "Dispatch Push"
                            result.onSuccess { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                dismiss()
                            }.onFailure { err ->
                                Toast.makeText(context, "Failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
