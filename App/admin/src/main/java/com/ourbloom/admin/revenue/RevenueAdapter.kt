package com.ourbloom.admin.revenue

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
import com.ourbloom.admin.data.models.RevenueRecord
import com.ourbloom.admin.data.models.TreasuryAuditLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class RevenueListItem {
    data class Revenue(val record: RevenueRecord) : RevenueListItem()
    data class Settlement(val date: String, val utr: String, val count: Int, val settledPaise: Long) : RevenueListItem()
    data class Audit(val log: TreasuryAuditLog) : RevenueListItem()
}

class RevenueAdapter : ListAdapter<RevenueListItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_REVENUE = 1
        private const val TYPE_SETTLEMENT = 2
        private const val TYPE_AUDIT = 3

        private val DiffCallback = object : DiffUtil.ItemCallback<RevenueListItem>() {
            override fun areItemsTheSame(oldItem: RevenueListItem, newItem: RevenueListItem): Boolean {
                return when {
                    oldItem is RevenueListItem.Revenue && newItem is RevenueListItem.Revenue ->
                        oldItem.record.eventId == newItem.record.eventId
                    oldItem is RevenueListItem.Settlement && newItem is RevenueListItem.Settlement ->
                        oldItem.date == newItem.date && oldItem.utr == newItem.utr
                    oldItem is RevenueListItem.Audit && newItem is RevenueListItem.Audit ->
                        oldItem.log.id == newItem.log.id
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: RevenueListItem, newItem: RevenueListItem): Boolean {
                return oldItem == newItem
            }
        }

        fun formatPaise(paise: Long): String {
            val rupees = paise / 100.0
            return if (paise % 100L == 0L) {
                "₹${rupees.toLong()}"
            } else {
                String.format(Locale.US, "₹%.2f", rupees)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is RevenueListItem.Revenue -> TYPE_REVENUE
            is RevenueListItem.Settlement -> TYPE_SETTLEMENT
            is RevenueListItem.Audit -> TYPE_AUDIT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_REVENUE -> {
                val view = inflater.inflate(R.layout.item_revenue_entry, parent, false)
                RevenueViewHolder(view)
            }
            TYPE_SETTLEMENT -> {
                val view = inflater.inflate(R.layout.item_settlement_batch, parent, false)
                SettlementViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_treasury_audit, parent, false)
                AuditViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is RevenueListItem.Revenue -> (holder as RevenueViewHolder).bind(item.record)
            is RevenueListItem.Settlement -> (holder as SettlementViewHolder).bind(item)
            is RevenueListItem.Audit -> (holder as AuditViewHolder).bind(item.log)
        }
    }

    class RevenueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_revenue_type_icon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_revenue_item_title)
        private val tvBadge: TextView = itemView.findViewById(R.id.tv_revenue_type_badge)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tv_revenue_item_subtitle)
        private val tvCoupleUser: TextView = itemView.findViewById(R.id.tv_revenue_couple_user)
        private val tvNetAmount: TextView = itemView.findViewById(R.id.tv_revenue_net_amount)
        private val tvGrossDetail: TextView = itemView.findViewById(R.id.tv_revenue_gross_detail)

        fun bind(record: RevenueRecord) {
            val context = itemView.context
            tvTitle.text = record.title.ifBlank { "${record.type} Revenue" }
            tvBadge.text = record.type.uppercase()

            val dateStr = if (record.timestamp > 0L) {
                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(record.timestamp))
            } else "Recent"

            val eventKey = record.eventId.ifBlank { record.id }.take(24)
            tvSubtitle.text = "Event: $eventKey • $dateStr"

            val coupleShort = if (record.coupleId.length > 8) record.coupleId.take(8) + "..." else record.coupleId
            val userDisplay = record.userName.ifBlank { "Partner" }
            tvCoupleUser.text = "Couple: $coupleShort • User: $userDisplay"

            tvNetAmount.text = "+${formatPaise(record.netRevenuePaise)}"
            tvGrossDetail.text = "Gross: ${formatPaise(record.grossAmountPaise)}"

            when (record.type.uppercase()) {
                "FEE" -> {
                    ivIcon.setImageResource(R.drawable.ic_revenue)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.admin_emerald))
                    tvBadge.setBackgroundResource(R.drawable.bg_status_verified)
                    tvBadge.setTextColor(ContextCompat.getColor(context, R.color.admin_emerald))
                }
                "GIFT" -> {
                    ivIcon.setImageResource(R.drawable.ic_gift)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.admin_blue))
                    tvBadge.setBackgroundResource(R.drawable.bg_input)
                    tvBadge.setTextColor(ContextCompat.getColor(context, R.color.admin_blue))
                }
                "SUBSCRIPTION" -> {
                    ivIcon.setImageResource(R.drawable.ic_subscription)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.admin_amber))
                    tvBadge.setBackgroundResource(R.drawable.bg_status_pending)
                    tvBadge.setTextColor(ContextCompat.getColor(context, R.color.admin_amber))
                }
                else -> {
                    ivIcon.setImageResource(R.drawable.ic_revenue)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.admin_navy))
                    tvBadge.setBackgroundResource(R.drawable.bg_input)
                    tvBadge.setTextColor(ContextCompat.getColor(context, R.color.admin_navy))
                }
            }
        }
    }

    class SettlementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tv_batch_date)
        private val tvUtr: TextView = itemView.findViewById(R.id.tv_batch_utr)
        private val tvCount: TextView = itemView.findViewById(R.id.tv_batch_txns_count)
        private val tvAmount: TextView = itemView.findViewById(R.id.tv_batch_settled_amount)

        fun bind(item: RevenueListItem.Settlement) {
            tvDate.text = "Batch Date: ${item.date}"
            tvUtr.text = "Merchant UTR: ${item.utr}"
            tvCount.text = "${item.count} deposit(s) reconciled into bank"
            tvAmount.text = formatPaise(item.settledPaise)
        }
    }

    class AuditViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAdminName: TextView = itemView.findViewById(R.id.tv_audit_admin_name)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tv_audit_timestamp)
        private val tvReason: TextView = itemView.findViewById(R.id.tv_audit_reason)
        private val tvChangeDetail: TextView = itemView.findViewById(R.id.tv_audit_change_detail)
        private val tvDocDetail: TextView = itemView.findViewById(R.id.tv_audit_document_detail)

        fun bind(log: TreasuryAuditLog) {
            tvAdminName.text = "Admin: ${log.adminName}"
            val dateStr = if (log.timestamp > 0L) {
                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(log.timestamp))
            } else "Recent"
            tvTimestamp.text = dateStr
            tvReason.text = "Reason: ${log.reason.ifBlank { "Treasury update" }}"

            val prevFd = formatPaise(log.previousFdPrincipalPaise)
            val newFd = formatPaise(log.newFdPrincipalPaise)
            val newLiq = formatPaise(log.newLiquidReservePaise)
            tvChangeDetail.text = "FD Principal: $prevFd → $newFd • Liquid Reserve: $newLiq"

            if (log.documentAction.isNotBlank() && log.documentAction != "UNCHANGED") {
                tvDocDetail.visibility = View.VISIBLE
                val docText = when (log.documentAction) {
                    "ATTACHED" -> "📄 Confirmation Doc: Attached ${log.newDocumentFileName}"
                    "REPLACED" -> "📄 Confirmation Doc: Replaced ${log.previousDocumentFileName} → ${log.newDocumentFileName}"
                    "REMOVED" -> "📄 Confirmation Doc: Removed ${log.previousDocumentFileName}"
                    else -> "📄 Confirmation Doc: ${log.documentAction}"
                }
                tvDocDetail.text = docText
            } else {
                tvDocDetail.visibility = View.GONE
            }
        }
    }
}
