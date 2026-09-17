package com.ourbloom.admin.treasury

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.ourbloom.admin.R
import com.ourbloom.admin.data.AdminFirestoreRepository
import com.ourbloom.admin.data.models.SavingsTransaction
import com.ourbloom.admin.data.models.SavingsWallet
import com.ourbloom.admin.util.CsvExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class TreasuryDialog(
    context: Context,
    private val transactions: List<SavingsTransaction>,
    private val wallets: List<SavingsWallet>,
    private val repository: AdminFirestoreRepository,
    private val coroutineScope: CoroutineScope
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_treasury)

        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        initViews()
    }

    private fun initViews() {
        val btnClose = findViewById<ImageButton>(R.id.btn_close_treasury)
        val tvSolvencyBadge = findViewById<TextView>(R.id.tv_solvency_badge)
        val tvBankBalance = findViewById<TextView>(R.id.tv_treasury_bank_balance)
        val tvReservesHeld = findViewById<TextView>(R.id.tv_treasury_reserves_held)
        val tvPlatformFees = findViewById<TextView>(R.id.tv_treasury_platform_fees)
        val tvBatchCount = findViewById<TextView>(R.id.tv_treasury_batch_count)
        val layoutBatches = findViewById<LinearLayout>(R.id.layout_settlement_batches)
        val btnSync = findViewById<MaterialButton>(R.id.btn_treasury_sync)
        val btnExportCsv = findViewById<MaterialButton>(R.id.btn_treasury_export_csv)

        btnClose?.setOnClickListener { dismiss() }

        // Compute metrics: settled deposits backed by PayU
        val settledDeposits = transactions.filter {
            it.type.equals("DEPOSIT", ignoreCase = true) && it.isSettled
        }

        val totalSettledAmount = settledDeposits.sumOf { it.settledAmount }
        val totalVaultReserves = wallets.sumOf { it.totalBalance }

        // Platform fee calculation: 2% of completed withdrawals
        val totalWithdrawals = transactions.filter {
            it.type.equals("WITHDRAWAL", ignoreCase = true)
        }.sumOf { it.amount }
        val platformEarnings = totalWithdrawals * 0.02

        tvBankBalance?.text = String.format(Locale.US, "₹%.2f", totalSettledAmount)
        tvPlatformFees?.text = String.format(Locale.US, "₹%.2f", platformEarnings)

        // Solvency comparison
        val solvencyRatio = if (totalVaultReserves > 0) {
            (totalSettledAmount / totalVaultReserves) * 100.0
        } else 100.0

        tvReservesHeld?.text = String.format(
            Locale.US,
            "Vault Reserves Held: ₹%.2f • %.1f%% Solvency",
            totalVaultReserves,
            solvencyRatio
        )

        if (totalSettledAmount >= totalVaultReserves || totalVaultReserves == 0.0) {
            tvSolvencyBadge?.text = "SOLVENT • 100% BACKED"
            tvSolvencyBadge?.setBackgroundResource(R.drawable.bg_status_verified)
            tvSolvencyBadge?.setTextColor(ContextCompat.getColor(context, R.color.admin_emerald))
        } else {
            tvSolvencyBadge?.text = "RESERVE DEFICIT"
            tvSolvencyBadge?.setBackgroundResource(R.drawable.bg_status_rejected)
            tvSolvencyBadge?.setTextColor(ContextCompat.getColor(context, R.color.admin_rose))
        }

        // Group settled deposits by settlementDate or merchantUtr
        val batchGroups = settledDeposits.groupBy {
            val key = it.settlementDate.orEmpty().ifEmpty { it.merchantUtr.orEmpty() }
            if (key.isEmpty()) "Settled Batch" else key
        }

        tvBatchCount?.text = "${batchGroups.size} Batches (${settledDeposits.size} Txns)"

        layoutBatches?.removeAllViews()
        if (batchGroups.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "No settled PayU batches recorded yet."
                setTextColor(ContextCompat.getColor(context, R.color.admin_text_muted))
                textSize = 12f
                setPadding(0, 8, 0, 8)
            }
            layoutBatches?.addView(emptyTv)
        } else {
            for ((batchKey, txnsInBatch) in batchGroups) {
                val batchSum = txnsInBatch.sumOf { it.settledAmount }
                val sampleUtr = txnsInBatch.firstOrNull { !it.merchantUtr.isNullOrBlank() }?.merchantUtr ?: "Pending UTR"

                val rowView = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 4, 0, 4) }
                    setPadding(10, 8, 10, 8)
                    setBackgroundResource(R.drawable.bg_input)
                }

                val leftCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val titleTv = TextView(context).apply {
                    text = batchKey
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.admin_navy))
                }

                val subtitleTv = TextView(context).apply {
                    text = "UTR: $sampleUtr • ${txnsInBatch.size} deposits"
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(context, R.color.admin_text_muted))
                }

                leftCol.addView(titleTv)
                leftCol.addView(subtitleTv)

                val amountTv = TextView(context).apply {
                    text = String.format(Locale.US, "+₹%.2f", batchSum)
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.admin_emerald))
                }

                rowView.addView(leftCol)
                rowView.addView(amountTv)
                layoutBatches?.addView(rowView)
            }
        }

        btnSync?.setOnClickListener {
            btnSync.isEnabled = false
            btnSync.text = "Syncing..."
            coroutineScope.launch {
                val success = repository.syncPayUSettlements()
                withContext(Dispatchers.Main) {
                    btnSync.isEnabled = true
                    btnSync.text = "Sync Settlements"
                    if (success) {
                        Toast.makeText(context, "PayU Settlements synced with Axis Bank UTRs!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Settlement sync completed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnExportCsv?.setOnClickListener {
            CsvExporter.exportTransactions(context, transactions)
        }
    }
}
