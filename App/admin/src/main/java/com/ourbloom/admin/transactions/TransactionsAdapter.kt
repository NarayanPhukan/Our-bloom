package com.ourbloom.admin.transactions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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

class TransactionsAdapter : ListAdapter<SavingsTransaction, TransactionsAdapter.TxViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    private fun formatRupees(paise: Long): String {
        val amount = BigDecimal.valueOf(paise, 2)
        return NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            currency = Currency.getInstance("INR")
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(amount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TxViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_transaction, parent, false)
        return TxViewHolder(view)
    }

    override fun onBindViewHolder(holder: TxViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TxViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val badgeType: FrameLayout = itemView.findViewById(R.id.badge_tx_type)
        private val imgType: ImageView = itemView.findViewById(R.id.img_tx_type)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_tx_title)
        private val tvUtr: TextView = itemView.findViewById(R.id.tv_tx_utr)
        private val tvNote: TextView = itemView.findViewById(R.id.tv_tx_note)
        private val tvFeeBreakdown: TextView = itemView.findViewById(R.id.tv_admin_tx_fee_breakdown)
        private val tvAmount: TextView = itemView.findViewById(R.id.tv_tx_amount)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_tx_date)

        fun bind(item: SavingsTransaction) {
            val isDeposit = item.type.equals("deposit", ignoreCase = true)
            val isReversal = item.type.equals("DEPOSIT_REVERSAL", ignoreCase = true)

            if (isReversal) {
                badgeType.setBackgroundResource(R.drawable.bg_status_rejected)
                imgType.setImageResource(R.drawable.ic_cancel)
                imgType.setColorFilter(ContextCompat.getColor(itemView.context, R.color.admin_crimson))
                val isFeeOnTop = item.pricingModel == "FEE_ON_TOP" || item.vaultAmountPaise > 0
                val vaultPaise = if (item.vaultAmountPaise > 0) item.vaultAmountPaise else (if (item.netVaultCreditPaise > 0) item.netVaultCreditPaise else Math.round(item.amount * 100))
                tvAmount.text = "-${formatRupees(vaultPaise)}"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_crimson))
                tvTitle.text = "Reversal • ${item.userName.ifBlank { "Partner" }}"

                if (isFeeOnTop) {
                    val refundPaise = if (item.payableAmountPaise > 0) item.payableAmountPaise else (if (item.grossAmountPaise > 0) item.grossAmountPaise else (vaultPaise + item.platformFeePaise))
                    tvFeeBreakdown.visibility = View.VISIBLE
                    tvFeeBreakdown.text = "Refund: ${formatRupees(refundPaise)} (Vault: ${formatRupees(vaultPaise)} • Fee: ${formatRupees(item.platformFeePaise)})"
                } else if (item.grossAmountPaise > 0) {
                    tvFeeBreakdown.visibility = View.VISIBLE
                    tvFeeBreakdown.text = "Refund: ${formatRupees(item.grossAmountPaise)} (Net: ${formatRupees(item.netVaultCreditPaise)} • Fee: ${formatRupees(item.platformFeePaise)})"
                } else {
                    tvFeeBreakdown.visibility = View.GONE
                }
            } else if (isDeposit) {
                badgeType.setBackgroundResource(R.drawable.bg_status_verified)
                imgType.setImageResource(R.drawable.ic_check_circle)
                imgType.setColorFilter(ContextCompat.getColor(itemView.context, R.color.admin_emerald))
                val isFeeOnTop = item.pricingModel == "FEE_ON_TOP" || item.vaultAmountPaise > 0

                if (isFeeOnTop) {
                    val vaultPaise = if (item.vaultAmountPaise > 0) item.vaultAmountPaise else item.netVaultCreditPaise
                    val paidPaise = if (item.payableAmountPaise > 0) item.payableAmountPaise else (if (item.grossAmountPaise > 0) item.grossAmountPaise else (vaultPaise + item.platformFeePaise))
                    tvAmount.text = "+${formatRupees(vaultPaise)}"
                    tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_emerald))
                    tvTitle.text = "Deposit • ${item.userName.ifBlank { "Partner" }}"

                    tvFeeBreakdown.visibility = View.VISIBLE
                    tvFeeBreakdown.text = "Vault: ${formatRupees(vaultPaise)} • Fee: ${formatRupees(item.platformFeePaise)} • Paid: ${formatRupees(paidPaise)} [Settlement: ${item.settlementStatus}]"
                } else {
                    val netPaise = if (item.netVaultCreditPaise > 0) item.netVaultCreditPaise else Math.round(item.amount * 100)
                    tvAmount.text = "+${formatRupees(netPaise)}"
                    tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_emerald))
                    tvTitle.text = "Deposit • ${item.userName.ifBlank { "Partner" }}"

                    if (item.grossAmountPaise > 0) {
                        tvFeeBreakdown.visibility = View.VISIBLE
                        tvFeeBreakdown.text = "Gross: ${formatRupees(item.grossAmountPaise)} • Fee: ${formatRupees(item.platformFeePaise)} • Net: ${formatRupees(item.netVaultCreditPaise)} [Settlement: ${item.settlementStatus}]"
                    } else {
                        tvFeeBreakdown.visibility = View.GONE
                    }
                }
            } else {
                badgeType.setBackgroundResource(R.drawable.bg_status_rejected)
                imgType.setImageResource(R.drawable.ic_cancel)
                imgType.setColorFilter(ContextCompat.getColor(itemView.context, R.color.admin_crimson))
                val paise = Math.round(item.amount * 100)
                tvAmount.text = "-${formatRupees(paise)}"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_crimson))
                tvTitle.text = "Payout • ${item.userName.ifBlank { "Partner" }}"
                tvFeeBreakdown.visibility = View.GONE
            }

            tvUtr.text = if (item.utrNumber.isNotBlank()) "Ref: ${item.utrNumber}" else "Payment: ${item.paymentMethod}"
            tvNote.text = item.note.ifBlank { item.category.ifBlank { "Vault Activity" } }

            val dateStr = if (item.timestamp > 0) dateFormat.format(Date(item.timestamp)) else "Recent"
            tvDate.text = dateStr
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<SavingsTransaction>() {
        override fun areItemsTheSame(oldItem: SavingsTransaction, newItem: SavingsTransaction): Boolean {
            return oldItem.id == newItem.id || (oldItem.utrNumber.isNotBlank() && oldItem.utrNumber == newItem.utrNumber)
        }

        override fun areContentsTheSame(oldItem: SavingsTransaction, newItem: SavingsTransaction): Boolean {
            return oldItem == newItem
        }
    }
}
