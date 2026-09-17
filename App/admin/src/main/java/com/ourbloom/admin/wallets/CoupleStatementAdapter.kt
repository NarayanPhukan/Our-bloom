package com.ourbloom.admin.wallets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ourbloom.admin.R
import com.ourbloom.admin.data.models.SavingsTransaction
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

class CoupleStatementAdapter : ListAdapter<SavingsTransaction, CoupleStatementAdapter.StatementViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_couple_statement_row, parent, false)
        return StatementViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatementViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StatementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_statement_icon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_statement_title)
        private val tvAmount: TextView = itemView.findViewById(R.id.tv_statement_amount)
        private val tvBreakdown: TextView = itemView.findViewById(R.id.tv_statement_breakdown)
        private val tvRef: TextView = itemView.findViewById(R.id.tv_statement_ref)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_statement_date)

        fun bind(item: SavingsTransaction) {
            val isDeposit = item.type.equals("deposit", ignoreCase = true)
            val isAdjustment = item.paymentMethod.contains("Admin", ignoreCase = true) || item.category == "Audit Correction"

            if (isAdjustment) {
                ivIcon.setImageResource(R.drawable.ic_lock)
                ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.admin_navy_light))
                tvTitle.text = "Internal Adjustment • ${item.userName.ifBlank { "Admin" }}"
                val sign = if (isDeposit) "+" else "-"
                val amountStr = if (item.amount % 1.0 == 0.0) item.amount.toInt().toString() else String.format(Locale.US, "%.2f", item.amount)
                tvAmount.text = "$sign₹$amountStr"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, if (isDeposit) R.color.admin_emerald else R.color.admin_crimson))
                tvBreakdown.text = "Audit Note: ${item.note.ifBlank { "Internal Correction" }}"
            } else if (isDeposit) {
                ivIcon.setImageResource(R.drawable.ic_check_circle)
                ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.admin_emerald))
                tvTitle.text = "Deposit • ${item.userName.ifBlank { "Partner" }}"

                val vaultPaise = if (item.vaultAmountPaise > 0) item.vaultAmountPaise else item.netVaultCreditPaise
                val paidPaise = if (item.payableAmountPaise > 0) item.payableAmountPaise else (if (item.grossAmountPaise > 0) item.grossAmountPaise else vaultPaise)

                tvAmount.text = "+${formatRupees(if (vaultPaise > 0) vaultPaise else Math.round(item.amount * 100))}"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_emerald))

                val settlementTag = if (item.isSettled) {
                    val ref = item.merchantUtr ?: item.providerBankReference
                    if (!ref.isNullOrBlank()) "Settled via PayU • Ref: $ref" else "Settled via PayU"
                } else {
                    "Settlement: ${item.settlementStatus}"
                }

                if (paidPaise > 0 && item.platformFeePaise > 0) {
                    tvBreakdown.text = "Vault: ${formatRupees(vaultPaise)} • Fee: ${formatRupees(item.platformFeePaise)} • Paid: ${formatRupees(paidPaise)} [$settlementTag]"
                } else {
                    tvBreakdown.text = "Method: ${item.paymentMethod} [$settlementTag]"
                }
            } else {
                ivIcon.setImageResource(R.drawable.ic_cancel)
                ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.admin_crimson))
                tvTitle.text = "Payout • ${item.userName.ifBlank { "Partner" }}"
                val paise = Math.round(item.amount * 100)
                tvAmount.text = "-${formatRupees(paise)}"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_crimson))
                tvBreakdown.text = "Disbursement: ${item.note.ifBlank { item.category }}"
            }

            val bankRef = item.merchantUtr ?: item.providerBankReference ?: item.utrNumber
            tvRef.text = if (bankRef.isNotBlank()) "Ref: $bankRef" else "Method: ${item.paymentMethod}"
            tvDate.text = if (item.timestamp > 0) dateFormat.format(Date(item.timestamp)) else "Recent"
        }

        private fun formatRupees(paise: Long): String {
            val amt = BigDecimal.valueOf(paise, 2)
            return NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
                currency = Currency.getInstance("INR")
                minimumFractionDigits = if (paise % 100 == 0L) 0 else 2
                maximumFractionDigits = 2
            }.format(amt)
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<SavingsTransaction>() {
        override fun areItemsTheSame(oldItem: SavingsTransaction, newItem: SavingsTransaction) =
            oldItem.id == newItem.id || (oldItem.utrNumber.isNotBlank() && oldItem.utrNumber == newItem.utrNumber)

        override fun areContentsTheSame(oldItem: SavingsTransaction, newItem: SavingsTransaction) =
            oldItem == newItem
    }
}
