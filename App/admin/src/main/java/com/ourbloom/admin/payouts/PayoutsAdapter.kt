package com.ourbloom.admin.payouts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.ourbloom.admin.R
import com.ourbloom.admin.data.models.WithdrawalRequest
import com.ourbloom.admin.util.ClipboardHelper
import java.util.Locale

class PayoutsAdapter(
    private val onApproveAndPayGatewayClick: (WithdrawalRequest) -> Unit,
    private val onRejectClick: (WithdrawalRequest) -> Unit
) : ListAdapter<WithdrawalRequest, PayoutsAdapter.PayoutViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PayoutViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_payout, parent, false)
        return PayoutViewHolder(view)
    }

    override fun onBindViewHolder(holder: PayoutViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PayoutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAmount: TextView = itemView.findViewById(R.id.tv_payout_amount)
        private val tvRequester: TextView = itemView.findViewById(R.id.tv_payout_requester)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_payout_status)
        private val tvCoupleId: TextView = itemView.findViewById(R.id.tv_payout_couple_id)
        private val tvMode: TextView = itemView.findViewById(R.id.tv_payout_mode)

        private val tvBankHolder: TextView = itemView.findViewById(R.id.tv_bank_holder)
        private val tvBankAccount: TextView = itemView.findViewById(R.id.tv_bank_account)
        private val btnCopyAccount: ImageButton = itemView.findViewById(R.id.btn_copy_account)
        private val tvBankIfsc: TextView = itemView.findViewById(R.id.tv_bank_ifsc)
        private val btnCopyIfsc: ImageButton = itemView.findViewById(R.id.btn_copy_ifsc)
        private val layoutUpiRow: View = itemView.findViewById(R.id.layout_upi_row)
        private val tvBankUpi: TextView = itemView.findViewById(R.id.tv_bank_upi)
        private val btnCopyUpi: ImageButton = itemView.findViewById(R.id.btn_copy_upi)

        private val layoutGatewayBadge: View = itemView.findViewById(R.id.layout_gateway_badge)
        private val tvRefInfo: TextView = itemView.findViewById(R.id.tv_payout_ref_info)
        private val layoutActions: LinearLayout = itemView.findViewById(R.id.layout_actions_payout)
        private val btnReject: MaterialButton = itemView.findViewById(R.id.btn_reject_payout)
        private val btnDisburse: MaterialButton = itemView.findViewById(R.id.btn_disburse_payout)

        fun bind(item: WithdrawalRequest) {
            val cleanAmount = if (item.amount % 1.0 == 0.0) item.amount.toInt().toString() else String.format(Locale.US, "%.2f", item.amount)
            tvAmount.text = "₹$cleanAmount"

            val requesterText = StringBuilder("By ${item.requestedByName.ifBlank { "Partner" }}")
            if (item.reason.isNotBlank()) requesterText.append(" • \"${item.reason}\"")
            if (item.isEmergency) requesterText.append(" • 🚨 EMERGENCY")
            tvRequester.text = requesterText.toString()

            tvCoupleId.text = "Couple: ${item.coupleId}"
            tvMode.text = if (item.payoutMode == "SEPARATED") "SPLIT TRANSFER" else "MUTUAL VAULT PAYOUT"

            // Target bank details
            val bankDetails = item.jointAccount ?: item.partner1Account
            val holderName = bankDetails?.accountHolderName?.ifBlank { item.requestedByName } ?: item.requestedByName
            val accNo = bankDetails?.accountNumber?.trim() ?: "Not specified"
            val ifsc = "${bankDetails?.ifscCode?.trim() ?: ""} (${bankDetails?.bankName ?: "Bank"})".trim()
            val upi = bankDetails?.upiId?.trim() ?: ""

            tvBankHolder.text = holderName.ifBlank { "Account Holder" }
            tvBankAccount.text = accNo
            tvBankIfsc.text = ifsc.ifBlank { "IFSC / Bank Details" }

            if (upi.isNotBlank()) {
                layoutUpiRow.visibility = View.VISIBLE
                tvBankUpi.text = upi
            } else {
                layoutUpiRow.visibility = View.GONE
            }

            // Copy buttons
            btnCopyAccount.setOnClickListener {
                ClipboardHelper.copyToClipboard(itemView.context, "Account Number", accNo)
            }
            btnCopyIfsc.setOnClickListener {
                val rawIfsc = bankDetails?.ifscCode?.trim() ?: ""
                ClipboardHelper.copyToClipboard(itemView.context, "IFSC Code", rawIfsc)
            }
            btnCopyUpi.setOnClickListener {
                ClipboardHelper.copyToClipboard(itemView.context, "UPI VPA", upi)
            }

            // Status Badge & Action layout visibility
            when (item.status) {
                "COMPLETED" -> {
                    tvStatus.text = "GATEWAY DISBURSED ✓"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_emerald))
                    tvStatus.setBackgroundResource(R.drawable.bg_status_verified)
                    layoutActions.visibility = View.GONE
                    layoutGatewayBadge.visibility = View.GONE

                    tvRefInfo.visibility = View.VISIBLE
                    tvRefInfo.text = "Transferred via PayU Gateway • Ref: ${item.payoutReference.ifBlank { "Processed" }} • Vault Updated"
                    tvRefInfo.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_emerald))
                }
                "REJECTED" -> {
                    tvStatus.text = "DECLINED ✕"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_crimson))
                    tvStatus.setBackgroundResource(R.drawable.bg_status_rejected)
                    layoutActions.visibility = View.GONE
                    layoutGatewayBadge.visibility = View.GONE

                    tvRefInfo.visibility = View.VISIBLE
                    tvRefInfo.text = "Declined: ${item.rejectionReason.ifBlank { "Declined by Admin" }}"
                    tvRefInfo.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_crimson))
                }
                "CANCELLED" -> {
                    tvStatus.text = "CANCELLED"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_text_muted))
                    tvStatus.setBackgroundResource(R.drawable.bg_admin_card)
                    layoutActions.visibility = View.GONE
                    layoutGatewayBadge.visibility = View.GONE
                    tvRefInfo.visibility = View.GONE
                }
                else -> { // PENDING_APPROVAL, WAITING_PERIOD, PROCESSING_PAYOUT
                    tvStatus.text = "ACTION NEEDED"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_amber))
                    tvStatus.setBackgroundResource(R.drawable.bg_status_pending)
                    layoutActions.visibility = View.VISIBLE
                    layoutGatewayBadge.visibility = View.VISIBLE
                    tvRefInfo.visibility = View.GONE

                    btnReject.text = "Decline"
                    btnDisburse.text = "Approve & Pay via Gateway ⚡"
                }
            }

            // Action triggers: 1-Tap Gateway Payout
            btnDisburse.setOnClickListener { onApproveAndPayGatewayClick(item) }
            btnReject.setOnClickListener { onRejectClick(item) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<WithdrawalRequest>() {
        override fun areItemsTheSame(oldItem: WithdrawalRequest, newItem: WithdrawalRequest): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WithdrawalRequest, newItem: WithdrawalRequest): Boolean {
            return oldItem == newItem
        }
    }
}
