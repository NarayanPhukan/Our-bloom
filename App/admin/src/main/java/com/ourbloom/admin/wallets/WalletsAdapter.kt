package com.ourbloom.admin.wallets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ourbloom.admin.R
import com.ourbloom.admin.data.models.SavingsWallet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WalletsAdapter(
    private val onAdjustClick: (SavingsWallet) -> Unit
) : ListAdapter<SavingsWallet, WalletsAdapter.WalletViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalletViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_wallet, parent, false)
        return WalletViewHolder(view)
    }

    override fun onBindViewHolder(holder: WalletViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class WalletViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCoupleId: TextView = itemView.findViewById(R.id.tv_wallet_couple_id)
        private val tvPartners: TextView = itemView.findViewById(R.id.tv_wallet_partners)
        private val tvBalance: TextView = itemView.findViewById(R.id.tv_wallet_balance)
        private val tvU1Label: TextView = itemView.findViewById(R.id.tv_wallet_u1_label)
        private val tvU1Amount: TextView = itemView.findViewById(R.id.tv_wallet_u1_amount)
        private val tvU2Label: TextView = itemView.findViewById(R.id.tv_wallet_u2_label)
        private val tvU2Amount: TextView = itemView.findViewById(R.id.tv_wallet_u2_amount)
        private val tvLastUpdated: TextView = itemView.findViewById(R.id.tv_wallet_last_updated)
        private val btnAdjust: Button = itemView.findViewById(R.id.btn_adjust_wallet)

        fun bind(item: SavingsWallet) {
            tvCoupleId.text = item.coupleId.ifBlank { item.id }

            val p1 = item.user1Name.ifBlank { "Partner 1" }
            val p2 = item.user2Name.ifBlank { "Partner 2" }
            tvPartners.text = "$p1 & $p2"

            val cleanBal = if (item.totalBalance % 1.0 == 0.0) item.totalBalance.toInt().toString() else String.format(Locale.US, "%.2f", item.totalBalance)
            tvBalance.text = "₹$cleanBal"

            tvU1Label.text = "$p1:"
            val u1Clean = if (item.user1Total % 1.0 == 0.0) item.user1Total.toInt().toString() else String.format(Locale.US, "%.2f", item.user1Total)
            tvU1Amount.text = "₹$u1Clean"

            tvU2Label.text = "$p2:"
            val u2Clean = if (item.user2Total % 1.0 == 0.0) item.user2Total.toInt().toString() else String.format(Locale.US, "%.2f", item.user2Total)
            tvU2Amount.text = "₹$u2Clean"

            val dateStr = if (item.lastUpdated > 0) "Updated: ${dateFormat.format(Date(item.lastUpdated))}" else "Active Vault"
            tvLastUpdated.text = dateStr

            btnAdjust.setOnClickListener { onAdjustClick(item) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<SavingsWallet>() {
        override fun areItemsTheSame(oldItem: SavingsWallet, newItem: SavingsWallet): Boolean {
            return oldItem.id == newItem.id || oldItem.coupleId == newItem.coupleId
        }

        override fun areContentsTheSame(oldItem: SavingsWallet, newItem: SavingsWallet): Boolean {
            return oldItem == newItem
        }
    }
}
