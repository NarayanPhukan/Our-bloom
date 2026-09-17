package com.ourbloom.app.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ourbloom.app.R
import com.ourbloom.app.data.models.SavingsTransaction
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

class SavingsTransactionsAdapter : RecyclerView.Adapter<SavingsTransactionsAdapter.TxnViewHolder>() {

    private val items = mutableListOf<SavingsTransaction>()
    private val dateFormat = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())

    private fun formatRupees(paise: Long): String {
        val amount = BigDecimal.valueOf(paise, 2)
        return NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            currency = Currency.getInstance("INR")
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(amount)
    }

    fun submitList(newItems: List<SavingsTransaction>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TxnViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_savings_transaction, parent, false)
        return TxnViewHolder(view)
    }

    override fun onBindViewHolder(holder: TxnViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class TxnViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIcon = itemView.findViewById<TextView>(R.id.tv_txn_type_icon)
        private val tvContributor = itemView.findViewById<TextView>(R.id.tv_txn_contributor)
        private val tvDate = itemView.findViewById<TextView>(R.id.tv_txn_date)
        private val tvAmount = itemView.findViewById<TextView>(R.id.tv_txn_amount)
        private val tvFeeBreakdown = itemView.findViewById<TextView>(R.id.tv_txn_fee_breakdown)
        private val tvNote = itemView.findViewById<TextView>(R.id.tv_txn_note)
        private val tvCategoryChip = itemView.findViewById<TextView>(R.id.tv_txn_category_chip)
        private val tvGoalChip = itemView.findViewById<TextView>(R.id.tv_txn_goal_chip)
        private val tvUtr = itemView.findViewById<TextView>(R.id.tv_txn_utr)

        fun bind(txn: SavingsTransaction) {
            tvContributor.text = txn.userName.ifBlank { "Partner" }
            tvDate.text = if (txn.timestamp > 0) dateFormat.format(Date(txn.timestamp)) else ""

            val isDeposit = txn.type.equals("deposit", ignoreCase = true)
            val isReversal = txn.type.equals("DEPOSIT_REVERSAL", ignoreCase = true)

            if (isReversal) {
                tvIcon.text = "↩️"
                val isFeeOnTop = txn.pricingModel == "FEE_ON_TOP" || txn.vaultAmountPaise > 0
                val vaultPaise = if (txn.vaultAmountPaise > 0) txn.vaultAmountPaise else (if (txn.netVaultCreditPaise > 0) txn.netVaultCreditPaise else Math.round(txn.amount * 100))
                tvAmount.text = "-${formatRupees(vaultPaise)}"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark))

                if (isFeeOnTop) {
                    val refundPaise = if (txn.payableAmountPaise > 0) txn.payableAmountPaise else (if (txn.grossAmountPaise > 0) txn.grossAmountPaise else (vaultPaise + txn.platformFeePaise))
                    tvFeeBreakdown.visibility = View.VISIBLE
                    tvFeeBreakdown.text = "Refund: ${formatRupees(refundPaise)} (Vault: ${formatRupees(vaultPaise)} • Fee: ${formatRupees(txn.platformFeePaise)})"
                } else if (txn.grossAmountPaise > 0) {
                    tvFeeBreakdown.visibility = View.VISIBLE
                    tvFeeBreakdown.text = "Refund: ${formatRupees(txn.grossAmountPaise)} (Net: ${formatRupees(txn.netVaultCreditPaise)} • Fee: ${formatRupees(txn.platformFeePaise)})"
                } else {
                    tvFeeBreakdown.visibility = View.GONE
                }
            } else if (isDeposit) {
                tvIcon.text = "💰"
                val isFeeOnTop = txn.pricingModel == "FEE_ON_TOP" || txn.vaultAmountPaise > 0

                if (isFeeOnTop) {
                    val vaultPaise = if (txn.vaultAmountPaise > 0) txn.vaultAmountPaise else txn.netVaultCreditPaise
                    val paidPaise = if (txn.payableAmountPaise > 0) txn.payableAmountPaise else (if (txn.grossAmountPaise > 0) txn.grossAmountPaise else (vaultPaise + txn.platformFeePaise))
                    tvAmount.text = "+${formatRupees(vaultPaise)}"
                    tvAmount.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))

                    tvFeeBreakdown.visibility = View.VISIBLE
                    tvFeeBreakdown.text = "Vault: ${formatRupees(vaultPaise)} • Fee: ${formatRupees(txn.platformFeePaise)} • Paid: ${formatRupees(paidPaise)}"
                } else if (txn.netVaultCreditPaise > 0) {
                    val netStr = formatRupees(txn.netVaultCreditPaise)
                    val grossStr = formatRupees(txn.grossAmountPaise)
                    val feeStr = formatRupees(txn.platformFeePaise)

                    tvAmount.text = "+$netStr"
                    tvAmount.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))

                    tvFeeBreakdown.visibility = View.VISIBLE
                    tvFeeBreakdown.text = "Deposit: $grossStr • 2% Fee: $feeStr • Net: +$netStr"
                } else {
                    val paise = Math.round(txn.amount * 100)
                    tvAmount.text = "+${formatRupees(paise)}"
                    tvAmount.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))
                    tvFeeBreakdown.visibility = View.GONE
                }
            } else {
                tvIcon.text = "💸"
                val paise = Math.round(txn.amount * 100)
                tvAmount.text = "-${formatRupees(paise)}"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark))
                tvFeeBreakdown.visibility = View.GONE
            }

            if (txn.note.isNotBlank()) {
                tvNote.visibility = View.VISIBLE
                tvNote.text = "“${txn.note}”"
            } else {
                tvNote.visibility = View.GONE
            }

            tvCategoryChip.text = txn.category.ifBlank {
                if (isReversal) "Reversal" else if (isDeposit) "Deposit" else "Withdrawal"
            }

            if (txn.goalTitle.isNotBlank()) {
                tvGoalChip.visibility = View.VISIBLE
                tvGoalChip.text = "🎯 ${txn.goalTitle}"
            } else {
                tvGoalChip.visibility = View.GONE
            }

            if (txn.utrNumber.isNotBlank()) {
                tvUtr.visibility = View.VISIBLE
                tvUtr.text = "Ref: ${txn.utrNumber}"
            } else if (txn.providerReference?.isNotBlank() == true) {
                tvUtr.visibility = View.VISIBLE
                tvUtr.text = "Ref: ${txn.providerReference}"
            } else {
                tvUtr.visibility = View.GONE
            }
        }
    }
}
