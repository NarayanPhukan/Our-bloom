package com.ourbloom.admin.wallets

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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.admin.R
import com.ourbloom.admin.data.AdminFirestoreRepository
import com.ourbloom.admin.data.models.SavingsTransaction
import com.ourbloom.admin.data.models.SavingsWallet
import com.ourbloom.admin.util.CsvExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CoupleDetailDialog(
    context: Context,
    private var wallet: SavingsWallet,
    private val repository: AdminFirestoreRepository,
    private val coroutineScope: CoroutineScope
) : Dialog(context) {

    private val adapter = CoupleStatementAdapter()
    private var txnsListener: ListenerRegistration? = null
    private var coupleTransactions: List<SavingsTransaction> = emptyList()

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_couple_detail)

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))

        initViews()
        loadStatement()
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btn_close_detail).setOnClickListener {
            dismiss()
        }

        updateWalletHeader()

        // Setup Statement RecyclerView
        val recycler = findViewById<RecyclerView>(R.id.recycler_couple_statement)
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter

        // Freeze / Unfreeze Button
        findViewById<MaterialButton>(R.id.btn_detail_freeze_toggle).setOnClickListener {
            showFreezeToggleDialog()
        }

        // Adjust Balance Button
        findViewById<MaterialButton>(R.id.btn_detail_adjust_balance).setOnClickListener {
            AdjustBalanceDialog(context, wallet) { newBalance, reason ->
                coroutineScope.launch {
                    val ok = repository.adjustWalletBalance(wallet.coupleId, newBalance, reason)
                    if (ok) {
                        wallet = wallet.copy(totalBalance = newBalance)
                        updateWalletHeader()
                        Toast.makeText(context, "Balance updated successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to update balance", Toast.LENGTH_SHORT).show()
                    }
                }
            }.show()
        }

        // Share Statement Button
        findViewById<MaterialButton>(R.id.btn_detail_share_statement).setOnClickListener {
            if (coupleTransactions.isNotEmpty()) {
                val cleanId = wallet.coupleId.take(8)
                CsvExporter.exportTransactionsToCsv(context, coupleTransactions, "Vault_Statement_$cleanId")
            } else {
                Toast.makeText(context, "No transactions to export", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateWalletHeader() {
        val tvNames = findViewById<TextView>(R.id.tv_detail_couple_names)
        val tvId = findViewById<TextView>(R.id.tv_detail_couple_id)
        val tvTotal = findViewById<TextView>(R.id.tv_detail_total_balance)
        val tvSplit = findViewById<TextView>(R.id.tv_detail_partner_split)
        val tvLock = findViewById<TextView>(R.id.tv_detail_lock_status)
        val tvFreezeBadge = findViewById<TextView>(R.id.tv_detail_freeze_badge)
        val btnFreeze = findViewById<MaterialButton>(R.id.btn_detail_freeze_toggle)

        val u1 = wallet.user1Name.ifBlank { "Partner 1" }
        val u2 = wallet.user2Name.ifBlank { "Partner 2" }
        tvNames.text = "$u1 & $u2"
        tvId.text = "Couple ID: ${wallet.coupleId}"

        val totalStr = if (wallet.totalBalance % 1.0 == 0.0) wallet.totalBalance.toInt().toString() else String.format(Locale.US, "%.2f", wallet.totalBalance)
        tvTotal.text = "₹$totalStr"

        val u1Str = if (wallet.user1Total % 1.0 == 0.0) wallet.user1Total.toInt().toString() else String.format(Locale.US, "%.2f", wallet.user1Total)
        val u2Str = if (wallet.user2Total % 1.0 == 0.0) wallet.user2Total.toInt().toString() else String.format(Locale.US, "%.2f", wallet.user2Total)
        tvSplit.text = "$u1: ₹$u1Str • $u2: ₹$u2Str"

        if (wallet.lockUntilDate > System.currentTimeMillis()) {
            tvLock.text = "🔒 Vault locked until ${dateFormat.format(Date(wallet.lockUntilDate))}"
            tvLock.setTextColor(ContextCompat.getColor(context, R.color.admin_amber))
        } else {
            tvLock.text = "🔓 Vault Unlocked"
            tvLock.setTextColor(ContextCompat.getColor(context, R.color.admin_text_muted))
        }

        if (wallet.isFrozen) {
            tvFreezeBadge.text = "FROZEN"
            tvFreezeBadge.setTextColor(ContextCompat.getColor(context, R.color.admin_crimson))
            tvFreezeBadge.setBackgroundResource(R.drawable.bg_status_rejected)
            btnFreeze.text = "Unfreeze Vault"
            btnFreeze.strokeColor = ContextCompat.getColorStateList(context, R.color.admin_emerald)
            btnFreeze.setTextColor(ContextCompat.getColor(context, R.color.admin_emerald))
        } else {
            tvFreezeBadge.text = "ACTIVE"
            tvFreezeBadge.setTextColor(ContextCompat.getColor(context, R.color.admin_emerald))
            tvFreezeBadge.setBackgroundResource(R.drawable.bg_status_verified)
            btnFreeze.text = "Freeze Vault"
            btnFreeze.strokeColor = ContextCompat.getColorStateList(context, R.color.admin_crimson)
            btnFreeze.setTextColor(ContextCompat.getColor(context, R.color.admin_crimson))
        }
    }

    private fun loadStatement() {
        val progress = findViewById<ProgressBar>(R.id.progress_statement_loading)
        val tvEmpty = findViewById<TextView>(R.id.tv_empty_statement)
        val tvHeading = findViewById<TextView>(R.id.tv_statement_heading)

        progress.visibility = View.VISIBLE
        txnsListener = repository.observeCoupleTransactions(wallet.coupleId) { list ->
            progress.visibility = View.GONE
            coupleTransactions = list
            adapter.submitList(list)
            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            tvHeading.text = "STATEMENT OF PAYMENTS (${list.size} records)"
        }
    }

    private fun showFreezeToggleDialog() {
        val willFreeze = !wallet.isFrozen
        val actionWord = if (willFreeze) "Freeze" else "Unfreeze"

        val input = EditText(context).apply {
            hint = if (willFreeze) "Reason for freezing (e.g. suspicious activity / dispute)" else "Reason for unfreezing"
            setPadding(32, 24, 32, 24)
        }

        AlertDialog.Builder(context)
            .setTitle("$actionWord Couple Vault?")
            .setMessage(
                if (willFreeze)
                    "Freezing will immediately halt all pending and future withdrawal disbursements for this couple."
                else
                    "Unfreezing will restore normal withdrawal and payout permissions for this couple."
            )
            .setView(input)
            .setPositiveButton(actionWord) { _, _ ->
                val reason = input.text.toString().trim().ifBlank { if (willFreeze) "Admin safety hold" else "Safety hold released" }
                coroutineScope.launch {
                    val ok = repository.freezeWallet(wallet.coupleId, willFreeze, reason)
                    if (ok) {
                        wallet = wallet.copy(isFrozen = willFreeze, freezeReason = reason)
                        updateWalletHeader()
                        Toast.makeText(context, "Vault successfully ${actionWord.lowercase()}d", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to update vault freeze status", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onStop() {
        super.onStop()
        txnsListener?.remove()
    }
}
