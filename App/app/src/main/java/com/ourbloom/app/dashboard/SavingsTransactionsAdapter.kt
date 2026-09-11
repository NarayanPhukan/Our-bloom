package com.ourbloom.app.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ourbloom.app.R
import com.ourbloom.app.data.models.SavingsTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavingsTransactionsAdapter : RecyclerView.Adapter<SavingsTransactionsAdapter.TxnViewHolder>() {

    private val items = mutableListOf<SavingsTransaction>()
    private val dateFormat = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())

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
        private val tvNote = itemView.findViewById<TextView>(R.id.tv_txn_note)
        private val tvCategoryChip = itemView.findViewById<TextView>(R.id.tv_txn_category_chip)
        private val tvGoalChip = itemView.findViewById<TextView>(R.id.tv_txn_goal_chip)
        private val tvUtr = itemView.findViewById<TextView>(R.id.tv_txn_utr)

        fun bind(txn: SavingsTransaction) {
            tvContributor.text = txn.userName.ifBlank { "Partner" }
            tvDate.text = if (txn.timestamp > 0) dateFormat.format(Date(txn.timestamp)) else ""

            val isDeposit = txn.type.equals("deposit", ignoreCase = true)
            val cleanAmount = if (txn.amount % 1.0 == 0.0) txn.amount.toInt().toString() else String.format(Locale.US, "%.2f", txn.amount)

            if (isDeposit) {
                tvIcon.text = "💰"
                tvAmount.text = "+₹$cleanAmount"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))
            } else {
                tvIcon.text = "💸"
                tvAmount.text = "-₹$cleanAmount"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark))
            }

            if (txn.note.isNotBlank()) {
                tvNote.visibility = View.VISIBLE
                tvNote.text = "“${txn.note}”"
            } else {
                tvNote.visibility = View.GONE
            }

            tvCategoryChip.text = txn.category.ifBlank { if (isDeposit) "Deposit" else "Withdrawal" }

            if (txn.goalTitle.isNotBlank()) {
                tvGoalChip.visibility = View.VISIBLE
                tvGoalChip.text = "🎯 ${txn.goalTitle}"
            } else {
                tvGoalChip.visibility = View.GONE
            }

            if (txn.utrNumber.isNotBlank()) {
                tvUtr.visibility = View.VISIBLE
                tvUtr.text = "UTR: ${txn.utrNumber}"
            } else {
                tvUtr.visibility = View.GONE
            }
        }
    }
}
