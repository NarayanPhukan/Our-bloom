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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionsAdapter : ListAdapter<SavingsTransaction, TransactionsAdapter.TxViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

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
        private val tvAmount: TextView = itemView.findViewById(R.id.tv_tx_amount)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_tx_date)

        fun bind(item: SavingsTransaction) {
            val isDeposit = item.type.equals("deposit", ignoreCase = true)
            val cleanAmount = if (item.amount % 1.0 == 0.0) item.amount.toInt().toString() else String.format(Locale.US, "%.2f", item.amount)

            if (isDeposit) {
                badgeType.setBackgroundResource(R.drawable.bg_status_verified)
                imgType.setImageResource(R.drawable.ic_check_circle)
                imgType.setColorFilter(ContextCompat.getColor(itemView.context, R.color.admin_emerald))
                tvAmount.text = "+₹$cleanAmount"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_emerald))
                tvTitle.text = "Deposit • ${item.userName.ifBlank { "Partner" }}"
            } else {
                badgeType.setBackgroundResource(R.drawable.bg_status_rejected)
                imgType.setImageResource(R.drawable.ic_cancel)
                imgType.setColorFilter(ContextCompat.getColor(itemView.context, R.color.admin_crimson))
                tvAmount.text = "-₹$cleanAmount"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_crimson))
                tvTitle.text = "Payout • ${item.userName.ifBlank { "Partner" }}"
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
