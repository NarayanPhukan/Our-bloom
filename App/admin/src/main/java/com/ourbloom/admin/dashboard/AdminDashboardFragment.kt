package com.ourbloom.admin.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.admin.R
import com.ourbloom.admin.data.AdminFirestoreRepository
import com.ourbloom.admin.data.models.SavingsTransaction
import com.ourbloom.admin.data.models.SavingsWallet
import com.ourbloom.admin.data.models.WithdrawalRequest
import com.ourbloom.admin.main.AdminMainActivity
import com.ourbloom.admin.transactions.ManualCreditDialog
import com.ourbloom.admin.transactions.TransactionsAdapter
import kotlinx.coroutines.launch
import java.util.Locale

class AdminDashboardFragment : Fragment() {

    private val repository = AdminFirestoreRepository()

    private var withListener: ListenerRegistration? = null
    private var txListener: ListenerRegistration? = null
    private var walletListener: ListenerRegistration? = null

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvTotalDeposits: TextView
    private lateinit var tvDepositsCount: TextView
    private lateinit var tvVaultReserves: TextView
    private lateinit var tvVaultsCount: TextView
    private lateinit var tvPendingPayouts: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvTotalDisbursed: TextView
    private lateinit var tvDisbursedCount: TextView

    private lateinit var layoutUrgentAlert: View
    private lateinit var tvUrgentTitle: TextView
    private lateinit var tvUrgentSubtitle: TextView

    private lateinit var recyclerRecent: RecyclerView
    private lateinit var tvEmptyActivity: TextView
    private val adapter = TransactionsAdapter()

    private var currentTransactions: List<SavingsTransaction> = emptyList()
    private var currentWallets: List<SavingsWallet> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_admin_dashboard, container, false)
        initViews(root)
        setupListeners()
        return root
    }

    private fun initViews(root: View) {
        swipeRefresh = root.findViewById(R.id.swipe_refresh_dashboard)
        tvTotalDeposits = root.findViewById(R.id.tv_kpi_total_deposits)
        tvDepositsCount = root.findViewById(R.id.tv_kpi_deposits_count)
        tvVaultReserves = root.findViewById(R.id.tv_kpi_vault_reserves)
        tvVaultsCount = root.findViewById(R.id.tv_kpi_vaults_count)
        tvPendingPayouts = root.findViewById(R.id.tv_kpi_pending_payouts)
        tvPendingCount = root.findViewById(R.id.tv_kpi_pending_count)
        tvTotalDisbursed = root.findViewById(R.id.tv_kpi_total_disbursed)
        tvDisbursedCount = root.findViewById(R.id.tv_kpi_disbursed_count)

        layoutUrgentAlert = root.findViewById(R.id.layout_urgent_alert)
        tvUrgentTitle = root.findViewById(R.id.tv_urgent_title)
        tvUrgentSubtitle = root.findViewById(R.id.tv_urgent_subtitle)

        recyclerRecent = root.findViewById(R.id.recycler_recent_activity)
        tvEmptyActivity = root.findViewById(R.id.tv_empty_activity)
        recyclerRecent.layoutManager = LinearLayoutManager(requireContext())
        recyclerRecent.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.syncPayUSettlements()
                swipeRefresh.isRefreshing = false
            }
        }

        root.findViewById<View>(R.id.btn_urgent_action).setOnClickListener {
            (activity as? AdminMainActivity)?.navigateToTab(R.id.nav_item_payouts)
        }

        root.findViewById<View>(R.id.btn_quick_disburse).setOnClickListener {
            (activity as? AdminMainActivity)?.navigateToTab(R.id.nav_item_payouts)
        }

        root.findViewById<View>(R.id.tv_view_all_transactions).setOnClickListener {
            (activity as? AdminMainActivity)?.navigateToTab(R.id.nav_item_ledger)
        }

        root.findViewById<View>(R.id.btn_quick_manual_credit).setOnClickListener {
            showManualCreditDialog()
        }

        root.findViewById<View>(R.id.card_hero_treasury)?.setOnClickListener {
            com.ourbloom.admin.treasury.TreasuryDialog(
                requireContext(),
                currentTransactions,
                currentWallets,
                repository,
                viewLifecycleOwner.lifecycleScope
            ).show()
        }

        // Bug Radar Strip
        val cardBugRadar = root.findViewById<View>(R.id.card_bug_radar_status)
        val tvBugSummary = root.findViewById<TextView>(R.id.tv_bug_radar_summary)
        val ivBugIcon = root.findViewById<android.widget.ImageView>(R.id.iv_bug_radar_icon)

        cardBugRadar?.setOnClickListener {
            com.ourbloom.admin.bugs.BugRadarDialog(requireContext()).show()
        }

        // Observe Bug Radar in real-time
        viewLifecycleOwner.lifecycleScope.launch {
            com.ourbloom.admin.bugs.AdminBugRadar.bugsState.collect { bugs ->
                if (!isAdded) return@collect
                if (bugs.isEmpty()) {
                    tvBugSummary?.text = "0 errors"
                    tvBugSummary?.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.admin_emerald))
                    ivBugIcon?.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.admin_emerald))
                } else {
                    val criticalCount = bugs.count { it.severity == com.ourbloom.admin.bugs.BugSeverity.CRITICAL }
                    val summaryText = if (criticalCount > 0) {
                        "${bugs.size} errors ($criticalCount critical)"
                    } else {
                        "${bugs.size} error(s) caught"
                    }
                    tvBugSummary?.text = summaryText
                    val alertColor = if (criticalCount > 0) R.color.admin_crimson else R.color.admin_amber
                    tvBugSummary?.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), alertColor))
                    ivBugIcon?.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), alertColor))
                }
            }
        }

        // App Update Card
        val tvAppVersionLabel = root.findViewById<TextView>(R.id.tv_app_version_label)
        tvAppVersionLabel.text = "Admin v${com.ourbloom.admin.BuildConfig.VERSION_NAME} (Build ${com.ourbloom.admin.BuildConfig.VERSION_CODE}) • Auto-updates enabled"

        val checkUpdateAction = View.OnClickListener {
            (activity as? androidx.appcompat.app.AppCompatActivity)?.let { act ->
                com.ourbloom.admin.updates.AdminUpdateManager.checkForUpdates(act, manualCheck = true)
            }
        }
        root.findViewById<View>(R.id.card_update_status).setOnClickListener(checkUpdateAction)
        root.findViewById<View>(R.id.tv_check_update_action).setOnClickListener(checkUpdateAction)
    }

    private fun showManualCreditDialog() {
        ManualCreditDialog(requireContext()) { coupleId, amount, utr, note ->
            viewLifecycleOwner.lifecycleScope.launch {
                val success = repository.manualCreditDeposit(coupleId, amount, utr, note)
                if (success) {
                    Toast.makeText(requireContext(), "₹${amount.toInt()} credited to vault $coupleId 🌸", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to credit vault. Check logs.", Toast.LENGTH_LONG).show()
                }
            }
        }.show()
    }

    private fun setupListeners() {
        // Trigger automatic PayU settlement sync on launch
        viewLifecycleOwner.lifecycleScope.launch {
            repository.syncPayUSettlements()
        }

        // 1. Observe Transactions for deposits calculation & recent activity
        txListener = repository.observeTransactions { transactions ->
            if (!isAdded) return@observeTransactions
            currentTransactions = transactions

            val settledDeposits = transactions.filter {
                it.type.equals("deposit", ignoreCase = true) &&
                it.isSettled
            }
            val pendingPayUDeposits = transactions.filter {
                it.type.equals("deposit", ignoreCase = true) &&
                !it.isSettled &&
                (it.paymentMethod.contains("PayU", ignoreCase = true) ||
                 it.paymentMethod.equals("Online", ignoreCase = true) ||
                 it.utrNumber.startsWith("OB_")) &&
                !it.paymentMethod.contains("Admin", ignoreCase = true) &&
                it.category != "Audit Correction"
            }

            val totalSettledSum = settledDeposits.sumOf { it.settledAmount }
            val cleanDep = if (totalSettledSum % 1.0 == 0.0) totalSettledSum.toInt().toString() else String.format(Locale.US, "%.2f", totalSettledSum)
            tvTotalDeposits.text = "₹$cleanDep"

            val countText = if (pendingPayUDeposits.isNotEmpty()) {
                "${settledDeposits.size} settled (${pendingPayUDeposits.size} pending)"
            } else {
                "${settledDeposits.size} settled deposits"
            }
            tvDepositsCount.text = countText

            // Show latest 5 transactions in recent activity
            val recent5 = transactions.take(5)
            adapter.submitList(recent5)
            tvEmptyActivity.visibility = if (recent5.isEmpty()) View.VISIBLE else View.GONE
        }

        // 2. Observe Wallets for total reserve balances
        walletListener = repository.observeSavingsWallets { wallets ->
            if (!isAdded) return@observeSavingsWallets
            currentWallets = wallets

            val totalReserves = wallets.sumOf { it.totalBalance }
            val cleanRes = if (totalReserves % 1.0 == 0.0) totalReserves.toInt().toString() else String.format(Locale.US, "%.2f", totalReserves)
            tvVaultReserves.text = "₹$cleanRes"
            tvVaultsCount.text = "${wallets.size} couple vaults"
        }

        // 3. Observe Withdrawal Requests for pending and completed payouts
        withListener = repository.observeWithdrawalRequests { requests ->
            if (!isAdded) return@observeWithdrawalRequests

            val pending = requests.filter {
                it.status == "PROCESSING_PAYOUT" || it.status == "WAITING_PERIOD" || it.status == "PENDING_APPROVAL"
            }
            val pendingSum = pending.sumOf { it.amount }
            val cleanPending = if (pendingSum % 1.0 == 0.0) pendingSum.toInt().toString() else String.format(Locale.US, "%.2f", pendingSum)
            tvPendingPayouts.text = "₹$cleanPending"
            tvPendingCount.text = "${pending.size} requests pending"

            val completed = requests.filter { it.status == "COMPLETED" }
            val completedSum = completed.sumOf { it.amount }
            val cleanCompleted = if (completedSum % 1.0 == 0.0) completedSum.toInt().toString() else String.format(Locale.US, "%.2f", completedSum)
            tvTotalDisbursed.text = "₹$cleanCompleted"
            tvDisbursedCount.text = "${completed.size} payouts completed"

            // Urgent Alert Banner
            if (pending.isNotEmpty()) {
                layoutUrgentAlert.visibility = View.VISIBLE
                tvUrgentTitle.text = "Action Needed: ${pending.size} Payouts Pending"
                tvUrgentSubtitle.text = "₹$cleanPending waiting for disbursement to partners"
            } else {
                layoutUrgentAlert.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        withListener?.remove()
        txListener?.remove()
        walletListener?.remove()
    }
}
