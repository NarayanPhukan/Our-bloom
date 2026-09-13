package com.ourbloom.admin.payouts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.ChipGroup
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.admin.R
import com.ourbloom.admin.data.AdminFirestoreRepository
import com.ourbloom.admin.data.models.WithdrawalRequest
import kotlinx.coroutines.launch
import java.util.Locale

class AdminPayoutsFragment : Fragment() {

    private val repository = AdminFirestoreRepository()
    private var listenerRegistration: ListenerRegistration? = null

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var chipGroup: ChipGroup

    private var allRequests: List<WithdrawalRequest> = emptyList()
    private var currentFilter: String = "PENDING"

    private val adapter = PayoutsAdapter(
        onApproveAndPayGatewayClick = { request -> handleGatewayPayout(request) },
        onRejectClick = { request -> handleReject(request) }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_admin_payouts, container, false)
        initViews(root)
        setupListener()
        return root
    }

    private fun initViews(root: View) {
        swipeRefresh = root.findViewById(R.id.swipe_refresh_payouts)
        recyclerView = root.findViewById(R.id.recycler_payouts)
        tvEmpty = root.findViewById(R.id.tv_empty_payouts)
        chipGroup = root.findViewById(R.id.chip_group_payout_filter)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            swipeRefresh.isRefreshing = false
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when {
                checkedIds.contains(R.id.chip_completed) -> "COMPLETED"
                checkedIds.contains(R.id.chip_rejected) -> "REJECTED"
                checkedIds.contains(R.id.chip_all) -> "ALL"
                else -> "PENDING"
            }
            applyFilter()
        }
    }

    private fun setupListener() {
        listenerRegistration = repository.observeWithdrawalRequests { list ->
            if (!isAdded) return@observeWithdrawalRequests
            allRequests = list
            applyFilter()
        }
    }

    private fun applyFilter() {
        val filtered = when (currentFilter) {
            "COMPLETED" -> allRequests.filter { it.status == "COMPLETED" }
            "REJECTED" -> allRequests.filter { it.status == "REJECTED" }
            "ALL" -> allRequests
            else -> allRequests.filter {
                it.status == "PROCESSING_PAYOUT" || it.status == "WAITING_PERIOD" || it.status == "PENDING_APPROVAL"
            }
        }

        adapter.submitList(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Automated Payment Gateway Payout:
     * Admin approves -> Payment Gateway handles the transfer -> Couple vault updated.
     * Zero manual work required by the administrator.
     */
    private fun handleGatewayPayout(request: WithdrawalRequest) {
        val cleanAmount = if (request.amount % 1.0 == 0.0) request.amount.toInt().toString() else String.format(Locale.US, "%.2f", request.amount)
        val bankDetails = request.jointAccount ?: request.partner1Account
        val holder = bankDetails?.accountHolderName?.ifBlank { request.requestedByName } ?: request.requestedByName
        val destination = if (!bankDetails?.upiId.isNullOrBlank()) {
            "UPI VPA: ${bankDetails?.upiId}"
        } else {
            "A/C: ${bankDetails?.accountNumber ?: "Bank"} (IFSC: ${bankDetails?.ifscCode ?: "N/A"})"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Approve & Pay via Gateway ⚡")
            .setMessage("The Payment Gateway will automatically transfer ₹$cleanAmount to $holder.\n\nDestination:\n$destination\n\n• Transfer handled automatically by PayU Gateway.\n• Couple vault balance will be updated instantly.\n• Push alert sent to both partners.")
            .setPositiveButton("Approve & Transfer 🌸") { _, _ ->
                Toast.makeText(requireContext(), "Processing payout via Payment Gateway...", Toast.LENGTH_SHORT).show()
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = repository.processGatewayPayout(request)
                    if (result.isSuccess) {
                        val ref = result.getOrNull() ?: ""
                        Toast.makeText(requireContext(), "Payout Successful! Transferred via Gateway (Ref: $ref) 🌸💳", Toast.LENGTH_LONG).show()
                    } else {
                        val err = result.exceptionOrNull()?.message ?: "Unknown error"
                        Toast.makeText(requireContext(), "Gateway payout error: $err", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleReject(request: WithdrawalRequest) {
        RejectRefundDialog(requireContext(), request) { reason ->
            viewLifecycleOwner.lifecycleScope.launch {
                val success = repository.rejectWithdrawal(request, reason)
                if (success) {
                    val msg = if (request.balanceDeducted) {
                        "Withdrawal REJECTED and ₹${request.amount.toInt()} refunded to couple vault! 🌸"
                    } else {
                        "Withdrawal request declined."
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to decline request. Check logs.", Toast.LENGTH_LONG).show()
                }
            }
        }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
    }
}
