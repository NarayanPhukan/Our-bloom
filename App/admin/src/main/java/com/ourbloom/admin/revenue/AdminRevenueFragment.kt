package com.ourbloom.admin.revenue

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.admin.R
import com.ourbloom.admin.data.AdminFirestoreRepository
import com.ourbloom.admin.data.TreasuryDocumentMeta
import com.ourbloom.admin.data.models.RevenueRecord
import com.ourbloom.admin.data.models.SavingsTransaction
import com.ourbloom.admin.data.models.SavingsWallet
import com.ourbloom.admin.data.models.TreasuryAuditLog
import com.ourbloom.admin.data.models.TreasuryPosition
import com.ourbloom.admin.data.models.WithdrawalRequest
import com.ourbloom.admin.profile.AdminProfileRepository
import com.ourbloom.admin.util.CsvExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AdminRevenueFragment : Fragment() {

    private val repository = AdminFirestoreRepository()

    private var onDocumentPickedCallback: ((Uri) -> Unit)? = null
    private val pickDocumentLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onDocumentPickedCallback?.invoke(it) }
    }

    private var revListener: ListenerRegistration? = null
    private var posListener: ListenerRegistration? = null
    private var auditListener: ListenerRegistration? = null
    private var txListener: ListenerRegistration? = null
    private var walletListener: ListenerRegistration? = null
    private var withListener: ListenerRegistration? = null

    // Views
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvSolvencyChip: TextView

    // Card 1: OurBloom Revenue
    private lateinit var tvTotalRevEvents: TextView
    private lateinit var tvNetRevTotal: TextView
    private lateinit var tvFeesAmount: TextView
    private lateinit var tvFeesCount: TextView
    private lateinit var tvGiftsAmount: TextView
    private lateinit var tvGiftsCount: TextView
    private lateinit var tvSubsAmount: TextView
    private lateinit var tvSubsCount: TextView

    // Card 2: Customer Funds
    private lateinit var tvVaultPrincipal: TextView
    private lateinit var tvVaultsCount: TextView
    private lateinit var tvPendingWithdrawals: TextView
    private lateinit var tvPendingWithdrawalsCount: TextView

    // Card 3: PayU Settlements
    private lateinit var tvSettledCash: TextView
    private lateinit var tvSettledCount: TextView
    private lateinit var tvPendingCash: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvReconciliationRate: TextView
    private lateinit var btnSyncPayU: MaterialButton

    // Card 4: Vault Treasury & Physical Backing
    private lateinit var tvImmediateLiquidity: TextView
    private lateinit var tvImmediateDetail: TextView
    private lateinit var tvExpectedCoverage: TextView
    private lateinit var tvExpectedDetail: TextView
    private lateinit var tvPnbFdSummary: TextView
    private lateinit var tvLiquidReserveSummary: TextView
    private lateinit var tvPayuInTransitSummary: TextView
    private lateinit var tvFdYieldSummary: TextView
    private lateinit var btnRecordTreasury: MaterialButton
    private lateinit var badgeFdDocStatus: TextView
    private lateinit var layoutDocDetails: LinearLayout
    private lateinit var tvFdDocFilename: TextView
    private lateinit var tvFdDocMeta: TextView
    private lateinit var btnViewFdDoc: MaterialButton
    private lateinit var btnReplaceFdDoc: MaterialButton
    private lateinit var layoutDocEmpty: LinearLayout
    private lateinit var btnUploadFdDoc: MaterialButton
    private lateinit var pbFdDocUpload: ProgressBar

    // Card 5: Canonical Ledger
    private lateinit var btnRecordManualRevenue: MaterialButton
    private lateinit var btnExportCsv: MaterialButton
    private lateinit var recyclerItems: RecyclerView
    private lateinit var tvEmpty: TextView
    private val adapter = RevenueAdapter()

    // Time filter buttons
    private lateinit var btnFilterAllTime: MaterialButton
    private lateinit var btnFilterThisMonth: MaterialButton
    private lateinit var btnFilterThisWeek: MaterialButton
    private lateinit var btnFilterToday: MaterialButton

    // Ledger tab buttons
    private lateinit var tabAllRevenue: MaterialButton
    private lateinit var tabFeesOnly: MaterialButton
    private lateinit var tabGiftsOnly: MaterialButton
    private lateinit var tabSubsOnly: MaterialButton
    private lateinit var tabSettlementsOnly: MaterialButton
    private lateinit var tabAuditOnly: MaterialButton

    // Active state
    private var allRevenueRecords: List<RevenueRecord> = emptyList()
    private var allTransactions: List<SavingsTransaction> = emptyList()
    private var allWallets: List<SavingsWallet> = emptyList()
    private var allWithdrawalRequests: List<WithdrawalRequest> = emptyList()
    private var currentTreasuryPosition: TreasuryPosition = TreasuryPosition()
    private var allAuditLogs: List<TreasuryAuditLog> = emptyList()

    private var activeTimeFilter = TimeFilter.ALL_TIME
    private var activeLedgerTab = LedgerTab.ALL_REVENUE

    enum class TimeFilter { ALL_TIME, THIS_MONTH, THIS_WEEK, TODAY }
    enum class LedgerTab { ALL_REVENUE, FEES, GIFTS, SUBSCRIPTIONS, SETTLEMENTS, AUDIT }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_admin_revenue, container, false)
        initViews(root)
        setupClickListeners(root)
        return root
    }

    private fun initViews(root: View) {
        swipeRefresh = root.findViewById(R.id.swipe_refresh_revenue)
        tvSolvencyChip = root.findViewById(R.id.tv_solvency_summary_chip)

        tvTotalRevEvents = root.findViewById(R.id.tv_total_revenue_events_count)
        tvNetRevTotal = root.findViewById(R.id.tv_net_revenue_total)
        tvFeesAmount = root.findViewById(R.id.tv_fees_revenue_amount)
        tvFeesCount = root.findViewById(R.id.tv_fees_revenue_count)
        tvGiftsAmount = root.findViewById(R.id.tv_gifts_revenue_amount)
        tvGiftsCount = root.findViewById(R.id.tv_gifts_revenue_count)
        tvSubsAmount = root.findViewById(R.id.tv_subs_revenue_amount)
        tvSubsCount = root.findViewById(R.id.tv_subs_revenue_count)

        tvVaultPrincipal = root.findViewById(R.id.tv_customer_vault_principal)
        tvVaultsCount = root.findViewById(R.id.tv_customer_vaults_count)
        tvPendingWithdrawals = root.findViewById(R.id.tv_pending_withdrawals_obligation)
        tvPendingWithdrawalsCount = root.findViewById(R.id.tv_pending_withdrawals_count)

        tvSettledCash = root.findViewById(R.id.tv_payu_settled_cash)
        tvSettledCount = root.findViewById(R.id.tv_payu_settled_count)
        tvPendingCash = root.findViewById(R.id.tv_payu_pending_cash)
        tvPendingCount = root.findViewById(R.id.tv_payu_pending_count)
        tvReconciliationRate = root.findViewById(R.id.tv_payu_reconciliation_rate)
        btnSyncPayU = root.findViewById(R.id.btn_sync_payu_settlements)

        tvImmediateLiquidity = root.findViewById(R.id.tv_immediate_liquidity_ratio)
        tvImmediateDetail = root.findViewById(R.id.tv_immediate_liquidity_detail)
        tvExpectedCoverage = root.findViewById(R.id.tv_expected_coverage_ratio)
        tvExpectedDetail = root.findViewById(R.id.tv_expected_coverage_detail)
        tvPnbFdSummary = root.findViewById(R.id.tv_pnb_fd_summary)
        tvLiquidReserveSummary = root.findViewById(R.id.tv_liquid_reserve_summary)
        tvPayuInTransitSummary = root.findViewById(R.id.tv_payu_intransit_summary)
        tvFdYieldSummary = root.findViewById(R.id.tv_fd_interest_yield_summary)
        btnRecordTreasury = root.findViewById(R.id.btn_record_treasury_position)

        badgeFdDocStatus = root.findViewById(R.id.badge_fd_doc_status)
        layoutDocDetails = root.findViewById(R.id.layout_doc_details)
        tvFdDocFilename = root.findViewById(R.id.tv_fd_doc_filename)
        tvFdDocMeta = root.findViewById(R.id.tv_fd_doc_meta)
        btnViewFdDoc = root.findViewById(R.id.btn_view_fd_doc)
        btnReplaceFdDoc = root.findViewById(R.id.btn_replace_fd_doc)
        layoutDocEmpty = root.findViewById(R.id.layout_doc_empty)
        btnUploadFdDoc = root.findViewById(R.id.btn_upload_fd_doc)
        pbFdDocUpload = root.findViewById(R.id.pb_fd_doc_upload)

        btnRecordManualRevenue = root.findViewById(R.id.btn_record_manual_revenue)
        btnExportCsv = root.findViewById(R.id.btn_export_revenue_csv)
        recyclerItems = root.findViewById(R.id.recycler_revenue_items)
        tvEmpty = root.findViewById(R.id.tv_empty_revenue)
        recyclerItems.layoutManager = LinearLayoutManager(requireContext())
        recyclerItems.adapter = adapter

        btnFilterAllTime = root.findViewById(R.id.btn_filter_all_time)
        btnFilterThisMonth = root.findViewById(R.id.btn_filter_this_month)
        btnFilterThisWeek = root.findViewById(R.id.btn_filter_this_week)
        btnFilterToday = root.findViewById(R.id.btn_filter_today)

        tabAllRevenue = root.findViewById(R.id.tab_all_revenue)
        tabFeesOnly = root.findViewById(R.id.tab_fees_only)
        tabGiftsOnly = root.findViewById(R.id.tab_gifts_only)
        tabSubsOnly = root.findViewById(R.id.tab_subs_only)
        tabSettlementsOnly = root.findViewById(R.id.tab_settlements_only)
        tabAuditOnly = root.findViewById(R.id.tab_audit_only)
    }

    private fun setupClickListeners(root: View) {
        root.findViewById<View>(R.id.btn_revenue_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        btnViewFdDoc.setOnClickListener {
            viewConfirmationDocument()
        }
        btnUploadFdDoc.setOnClickListener {
            promptUploadConfirmationDocument(isReplacing = false)
        }
        btnReplaceFdDoc.setOnClickListener {
            promptUploadConfirmationDocument(isReplacing = true)
        }

        swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.syncPayUSettlements()
                repository.syncHistoricalFeesToRevenueLedger()
                swipeRefresh.isRefreshing = false
            }
        }

        btnSyncPayU.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                Toast.makeText(requireContext(), "Syncing PayU settlements...", Toast.LENGTH_SHORT).show()
                val ok = repository.syncPayUSettlements()
                if (ok) {
                    Toast.makeText(requireContext(), "PayU settlements synchronized 🌸", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Settlement sync completed.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnRecordTreasury.setOnClickListener {
            showRecordTreasuryDialog()
        }

        btnRecordManualRevenue.setOnClickListener {
            showRecordManualRevenueDialog()
        }

        btnExportCsv.setOnClickListener {
            val filtered = getFilteredRevenueList()
            if (filtered.isNotEmpty()) {
                CsvExporter.exportRevenueToCsv(requireContext(), filtered)
            } else {
                Toast.makeText(requireContext(), "No revenue records to export", Toast.LENGTH_SHORT).show()
            }
        }

        // Time Filters
        btnFilterAllTime.setOnClickListener { setTimeFilter(TimeFilter.ALL_TIME) }
        btnFilterThisMonth.setOnClickListener { setTimeFilter(TimeFilter.THIS_MONTH) }
        btnFilterThisWeek.setOnClickListener { setTimeFilter(TimeFilter.THIS_WEEK) }
        btnFilterToday.setOnClickListener { setTimeFilter(TimeFilter.TODAY) }

        // Ledger Tabs
        tabAllRevenue.setOnClickListener { setLedgerTab(LedgerTab.ALL_REVENUE) }
        tabFeesOnly.setOnClickListener { setLedgerTab(LedgerTab.FEES) }
        tabGiftsOnly.setOnClickListener { setLedgerTab(LedgerTab.GIFTS) }
        tabSubsOnly.setOnClickListener { setLedgerTab(LedgerTab.SUBSCRIPTIONS) }
        tabSettlementsOnly.setOnClickListener { setLedgerTab(LedgerTab.SETTLEMENTS) }
        tabAuditOnly.setOnClickListener { setLedgerTab(LedgerTab.AUDIT) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Initial background historical fee backfill check
        viewLifecycleOwner.lifecycleScope.launch {
            repository.syncHistoricalFeesToRevenueLedger()
        }

        // 2. Start Live Observers
        observeAllLedgers()
    }

    private fun observeAllLedgers() {
        revListener = repository.observeRevenueRecords { records ->
            if (!isAdded) return@observeRevenueRecords
            allRevenueRecords = records
            recalculateAllMetrics()
        }

        posListener = repository.observeTreasuryPosition { pos ->
            if (!isAdded) return@observeTreasuryPosition
            currentTreasuryPosition = pos
            recalculateAllMetrics()
        }

        auditListener = repository.observeTreasuryAuditLog { logs ->
            if (!isAdded) return@observeTreasuryAuditLog
            allAuditLogs = logs
            if (activeLedgerTab == LedgerTab.AUDIT) {
                renderRecyclerItems()
            }
        }

        txListener = repository.observeTransactions { txns ->
            if (!isAdded) return@observeTransactions
            allTransactions = txns
            recalculateAllMetrics()
        }

        walletListener = repository.observeSavingsWallets { wallets ->
            if (!isAdded) return@observeSavingsWallets
            allWallets = wallets
            recalculateAllMetrics()
        }

        withListener = repository.observeWithdrawalRequests { requests ->
            if (!isAdded) return@observeWithdrawalRequests
            allWithdrawalRequests = requests
            recalculateAllMetrics()
        }
    }

    private fun recalculateAllMetrics() {
        val filteredRevenues = getFilteredRevenueList()

        // 1. OURBLOOM REVENUE (Company Earned Income)
        val netRevTotalPaise = filteredRevenues.sumOf { it.netRevenuePaise }
        val feesList = filteredRevenues.filter { it.type.equals("FEE", true) }
        val giftsList = filteredRevenues.filter { it.type.equals("GIFT", true) }
        val subsList = filteredRevenues.filter { it.type.equals("SUBSCRIPTION", true) }

        val feesTotalPaise = feesList.sumOf { it.netRevenuePaise }
        val giftsTotalPaise = giftsList.sumOf { it.netRevenuePaise }
        val subsTotalPaise = subsList.sumOf { it.netRevenuePaise }

        tvNetRevTotal.text = RevenueAdapter.formatPaise(netRevTotalPaise)
        tvTotalRevEvents.text = "${filteredRevenues.size} receipts"
        tvFeesAmount.text = RevenueAdapter.formatPaise(feesTotalPaise)
        tvFeesCount.text = "${feesList.size} fees • 2% on deposits"
        tvGiftsAmount.text = RevenueAdapter.formatPaise(giftsTotalPaise)
        tvGiftsCount.text = "${giftsList.size} gifts • Net margin"
        tvSubsAmount.text = RevenueAdapter.formatPaise(subsTotalPaise)
        tvSubsCount.text = "${subsList.size} subscriptions"

        // 2. CUSTOMER FUNDS (Liabilities — Never counted as revenue)
        val vaultPrincipalPaise = allWallets.sumOf { Math.round(it.totalBalance * 100.0) }
        val pendingWithdrawals = allWithdrawalRequests.filter {
            it.status == "PROCESSING_PAYOUT" || it.status == "WAITING_PERIOD" || it.status == "PENDING_APPROVAL"
        }
        val pendingWithdrawalsPaise = pendingWithdrawals.sumOf { Math.round(it.amount * 100.0) }

        tvVaultPrincipal.text = RevenueAdapter.formatPaise(vaultPrincipalPaise)
        tvVaultsCount.text = "${allWallets.size} couple vaults"
        tvPendingWithdrawals.text = RevenueAdapter.formatPaise(pendingWithdrawalsPaise)
        tvPendingWithdrawalsCount.text = "${pendingWithdrawals.size} payouts pending"

        // 3. PAYU SETTLEMENTS (Cashflow & Gateway Status)
        val settledTxns = allTransactions.filter { it.type.equals("deposit", true) && it.isSettled }
        val settledCashPaise = settledTxns.sumOf { it.effectiveSettledPaise }

        val pendingTxns = allTransactions.filter {
            it.type.equals("deposit", true) && !it.isSettled &&
            (it.paymentMethod.contains("PayU", true) || it.paymentMethod.equals("Online", true) || it.utrNumber.startsWith("OB_")) &&
            !it.paymentMethod.contains("Admin", true) && it.category != "Audit Correction"
        }
        val pendingCashPaise = pendingTxns.sumOf { it.effectiveVaultPaise }

        tvSettledCash.text = RevenueAdapter.formatPaise(settledCashPaise)
        tvSettledCount.text = "${settledTxns.size} settled deposits"
        tvPendingCash.text = RevenueAdapter.formatPaise(pendingCashPaise)
        tvPendingCount.text = "${pendingTxns.size} in-transit batch"

        val totalInflowPaise = settledCashPaise + pendingCashPaise
        val reconciledRate = if (totalInflowPaise > 0L) {
            (settledCashPaise.toDouble() / totalInflowPaise.toDouble()) * 100.0
        } else 100.0

        val lastUtr = settledTxns.firstOrNull { !it.merchantUtr.isNullOrBlank() }?.merchantUtr ?: "AXISCN1470216334"
        val lastDate = settledTxns.firstOrNull { !it.settlementDate.isNullOrBlank() }?.settlementDate ?: "Recent"
        tvReconciliationRate.text = String.format(Locale.US, "Reconciliation Rate: %.1f%% • Last: %s (%s)", reconciledRate, lastDate, lastUtr)

        // 4. VAULT TREASURY & PHYSICAL BACKING (ACTIVE PNB FD POSITION)
        // Liquid bank cash is at least the settled cash received in bank
        val liquidBankReservePaise = currentTreasuryPosition.liquidBankReservePaise.coerceAtLeast(settledCashPaise)
        val fdPrincipalPaise = currentTreasuryPosition.fdPrincipalPaise

        // Immediate Liquidity = Liquid Bank Cash / Immediate Withdrawal Obligations (Customer Principal)
        val immediateLiquidityRatio = if (vaultPrincipalPaise > 0L) {
            (liquidBankReservePaise.toDouble() / vaultPrincipalPaise.toDouble()) * 100.0
        } else 100.0

        // Expected Coverage = (Liquid Bank Cash + In-Transit PayU + PNB FD Principal) / Customer Principal
        val totalBackingPaise = liquidBankReservePaise + pendingCashPaise + fdPrincipalPaise
        val expectedCoverageRatio = if (vaultPrincipalPaise > 0L) {
            (totalBackingPaise.toDouble() / vaultPrincipalPaise.toDouble()) * 100.0
        } else 100.0

        tvImmediateLiquidity.text = String.format(Locale.US, "%.1f%%", immediateLiquidityRatio)
        tvImmediateDetail.text = "${RevenueAdapter.formatPaise(liquidBankReservePaise)} ÷ ${RevenueAdapter.formatPaise(vaultPrincipalPaise)}"

        tvExpectedCoverage.text = String.format(Locale.US, "%.1f%%", expectedCoverageRatio)
        tvExpectedDetail.text = "${RevenueAdapter.formatPaise(totalBackingPaise)} ÷ ${RevenueAdapter.formatPaise(vaultPrincipalPaise)}"

        val fdRefDisplay = if (currentTreasuryPosition.fdReferenceNumber.isNotBlank()) currentTreasuryPosition.fdReferenceNumber else "None recorded"
        val rateDisplay = if (currentTreasuryPosition.fdInterestRateBps > 0L) String.format(Locale.US, "%.2f%%", currentTreasuryPosition.fdInterestRateBps / 100.0) else "0.0%"
        tvPnbFdSummary.text = "• Active PNB FD Principal: ${RevenueAdapter.formatPaise(fdPrincipalPaise)} (Ref: $fdRefDisplay @ $rateDisplay)"
        tvLiquidReserveSummary.text = "• Liquid Checking Reserve: ${RevenueAdapter.formatPaise(liquidBankReservePaise)}"
        tvPayuInTransitSummary.text = "• PayU Receivable [In Transit]: ${RevenueAdapter.formatPaise(pendingCashPaise)}"

        val projectedYieldPaise = if (currentTreasuryPosition.fdMaturityValuePaise > fdPrincipalPaise) {
            currentTreasuryPosition.fdMaturityValuePaise - fdPrincipalPaise
        } else {
            // Projected based on annual rate
            (fdPrincipalPaise * currentTreasuryPosition.fdInterestRateBps) / 10000L
        }
        tvFdYieldSummary.text = "• Contracted FD Yield: ${RevenueAdapter.formatPaise(projectedYieldPaise)} (OurBloom Interest Income — separated)"

        // Solvency Header Badge
        when {
            immediateLiquidityRatio >= 100.0 -> {
                tvSolvencyChip.text = "100% LIQUID"
                tvSolvencyChip.setBackgroundResource(R.drawable.bg_status_verified)
                tvSolvencyChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_emerald))
            }
            expectedCoverageRatio >= 100.0 -> {
                tvSolvencyChip.text = String.format(Locale.US, "%.0f%% LIQUID • 100%% COVERED", immediateLiquidityRatio)
                tvSolvencyChip.setBackgroundResource(R.drawable.bg_status_verified)
                tvSolvencyChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_emerald))
            }
            else -> {
                tvSolvencyChip.text = String.format(Locale.US, "DEFICIT (%.0f%% COVERAGE)", expectedCoverageRatio)
                tvSolvencyChip.setBackgroundResource(R.drawable.bg_status_rejected)
                tvSolvencyChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_crimson))
            }
        }

        // 5. PNB e-FD Confirmation Document UI State
        if (currentTreasuryPosition.documentUrl.isNotBlank()) {
            badgeFdDocStatus.text = "✓ PNB e-FD Attached"
            badgeFdDocStatus.setBackgroundResource(R.drawable.bg_status_verified)
            badgeFdDocStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_emerald))
            layoutDocDetails.visibility = View.VISIBLE
            layoutDocEmpty.visibility = View.GONE
            tvFdDocFilename.text = currentTreasuryPosition.documentFileName.ifBlank { "PNB_eFD_Confirmation.pdf" }
            val sizeKb = if (currentTreasuryPosition.documentSizeBytes > 0) "${currentTreasuryPosition.documentSizeBytes / 1024} KB • " else ""
            val dateStr = if (currentTreasuryPosition.documentUploadedAt > 0) {
                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(currentTreasuryPosition.documentUploadedAt))
            } else "Verified"
            tvFdDocMeta.text = "${sizeKb}Uploaded by ${currentTreasuryPosition.documentUploadedBy.ifBlank { "Admin" }} on $dateStr"
        } else {
            badgeFdDocStatus.text = "No PDF Attached"
            badgeFdDocStatus.setBackgroundResource(R.drawable.bg_status_pending)
            badgeFdDocStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_amber))
            layoutDocDetails.visibility = View.GONE
            layoutDocEmpty.visibility = View.VISIBLE
        }

        renderRecyclerItems()
    }

    private fun getFilteredRevenueList(): List<RevenueRecord> {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        return when (activeTimeFilter) {
            TimeFilter.ALL_TIME -> allRevenueRecords
            TimeFilter.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                val startOfMonth = calendar.timeInMillis
                allRevenueRecords.filter { it.timestamp >= startOfMonth }
            }
            TimeFilter.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                val startOfWeek = calendar.timeInMillis
                allRevenueRecords.filter { it.timestamp >= startOfWeek }
            }
            TimeFilter.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                val startOfDay = calendar.timeInMillis
                allRevenueRecords.filter { it.timestamp >= startOfDay }
            }
        }
    }

    private fun renderRecyclerItems() {
        val list = mutableListOf<RevenueListItem>()

        when (activeLedgerTab) {
            LedgerTab.ALL_REVENUE -> {
                list.addAll(getFilteredRevenueList().map { RevenueListItem.Revenue(it) })
            }
            LedgerTab.FEES -> {
                list.addAll(getFilteredRevenueList().filter { it.type.equals("FEE", true) }.map { RevenueListItem.Revenue(it) })
            }
            LedgerTab.GIFTS -> {
                list.addAll(getFilteredRevenueList().filter { it.type.equals("GIFT", true) }.map { RevenueListItem.Revenue(it) })
            }
            LedgerTab.SUBSCRIPTIONS -> {
                list.addAll(getFilteredRevenueList().filter { it.type.equals("SUBSCRIPTION", true) }.map { RevenueListItem.Revenue(it) })
            }
            LedgerTab.SETTLEMENTS -> {
                val settledTxns = allTransactions.filter { it.type.equals("deposit", true) && it.isSettled }
                val grouped = settledTxns.groupBy { it.settlementDate.orEmpty().ifEmpty { it.merchantUtr.orEmpty().ifEmpty { "Settled Batch" } } }
                for ((batchKey, batchTxns) in grouped) {
                    val sampleUtr = batchTxns.firstOrNull { !it.merchantUtr.isNullOrBlank() }?.merchantUtr ?: "AXISCN1470216334"
                    val batchPaise = batchTxns.sumOf { it.effectiveSettledPaise }
                    list.add(RevenueListItem.Settlement(batchKey, sampleUtr, batchTxns.size, batchPaise))
                }
            }
            LedgerTab.AUDIT -> {
                list.addAll(allAuditLogs.map { RevenueListItem.Audit(it) })
            }
        }

        adapter.submitList(list)
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setTimeFilter(filter: TimeFilter) {
        activeTimeFilter = filter
        btnFilterAllTime.setStyleSelected(filter == TimeFilter.ALL_TIME)
        btnFilterThisMonth.setStyleSelected(filter == TimeFilter.THIS_MONTH)
        btnFilterThisWeek.setStyleSelected(filter == TimeFilter.THIS_WEEK)
        btnFilterToday.setStyleSelected(filter == TimeFilter.TODAY)
        recalculateAllMetrics()
    }

    private fun setLedgerTab(tab: LedgerTab) {
        activeLedgerTab = tab
        tabAllRevenue.setStyleSelected(tab == LedgerTab.ALL_REVENUE)
        tabFeesOnly.setStyleSelected(tab == LedgerTab.FEES)
        tabGiftsOnly.setStyleSelected(tab == LedgerTab.GIFTS)
        tabSubsOnly.setStyleSelected(tab == LedgerTab.SUBSCRIPTIONS)
        tabSettlementsOnly.setStyleSelected(tab == LedgerTab.SETTLEMENTS)
        tabAuditOnly.setStyleSelected(tab == LedgerTab.AUDIT)
        renderRecyclerItems()
    }

    private fun MaterialButton.setStyleSelected(selected: Boolean) {
        if (selected) {
            setBackgroundColor(ContextCompat.getColor(context, R.color.admin_navy))
            setTextColor(Color.WHITE)
            strokeWidth = 0
        } else {
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(ContextCompat.getColor(context, R.color.admin_text_secondary))
            strokeWidth = (1 * resources.displayMetrics.density).toInt()
            strokeColor = ContextCompat.getColorStateList(context, R.color.admin_divider)
        }
    }

    private fun showRecordTreasuryDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_record_treasury_position)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val etRef = dialog.findViewById<EditText>(R.id.et_treasury_fd_ref)
        val etPrincipal = dialog.findViewById<EditText>(R.id.et_treasury_fd_principal)
        val etRate = dialog.findViewById<EditText>(R.id.et_treasury_fd_rate)
        val etMaturity = dialog.findViewById<EditText>(R.id.et_treasury_fd_maturity)
        val etLiquid = dialog.findViewById<EditText>(R.id.et_treasury_liquid_reserve)
        val etReason = dialog.findViewById<EditText>(R.id.et_treasury_update_reason)

        // Pre-fill existing state
        if (currentTreasuryPosition.fdReferenceNumber.isNotBlank()) {
            etRef.setText(currentTreasuryPosition.fdReferenceNumber)
        }
        if (currentTreasuryPosition.fdPrincipalPaise > 0L) {
            etPrincipal.setText((currentTreasuryPosition.fdPrincipalPaise / 100.0).toInt().toString())
        }
        if (currentTreasuryPosition.fdInterestRateBps > 0L) {
            etRate.setText(String.format(Locale.US, "%.2f", currentTreasuryPosition.fdInterestRateBps / 100.0))
        }
        if (currentTreasuryPosition.liquidBankReservePaise > 0L) {
            etLiquid.setText((currentTreasuryPosition.liquidBankReservePaise / 100.0).toInt().toString())
        }

        val tvDocStatus = dialog.findViewById<TextView>(R.id.tv_dialog_current_doc_status)
        val btnPickDoc = dialog.findViewById<MaterialButton>(R.id.btn_dialog_pick_fd_doc)
        val tvSelectedDocName = dialog.findViewById<TextView>(R.id.tv_dialog_selected_doc_name)

        if (currentTreasuryPosition.documentUrl.isNotBlank()) {
            val fileName = currentTreasuryPosition.documentFileName.ifBlank { "PNB_eFD_Receipt.pdf" }
            tvDocStatus.text = "Current: $fileName (Uploaded by ${currentTreasuryPosition.documentUploadedBy})"
        } else {
            tvDocStatus.text = "No document currently attached"
        }

        var dialogUploadedDocMeta: TreasuryDocumentMeta? = null

        btnPickDoc.setOnClickListener {
            onDocumentPickedCallback = { uri ->
                viewLifecycleOwner.lifecycleScope.launch {
                    tvSelectedDocName.text = "Uploading PDF..."
                    val resolver = requireContext().contentResolver
                    val fileName = getFileNameFromUri(uri)
                    val mimeType = resolver.getType(uri) ?: "application/pdf"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val adminProfile = AdminProfileRepository.getProfile()
                        val uploadRes = repository.uploadFdConfirmationDocument(
                            fileBytes = bytes,
                            originalFileName = fileName,
                            mimeType = mimeType,
                            adminEmail = adminProfile.email.ifBlank { "admin@ourbloom.app" }
                        )
                        if (uploadRes.isSuccess) {
                            dialogUploadedDocMeta = uploadRes.getOrThrow()
                            tvSelectedDocName.text = "✓ Ready: $fileName"
                            Toast.makeText(requireContext(), "Document ready to attach on save", Toast.LENGTH_SHORT).show()
                        } else {
                            tvSelectedDocName.text = "Upload failed"
                            Toast.makeText(requireContext(), "Upload failed: ${uploadRes.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            pickDocumentLauncher.launch("*/*")
        }

        dialog.findViewById<View>(R.id.btn_close_treasury_dialog).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btn_cancel_treasury).setOnClickListener { dialog.dismiss() }

        dialog.findViewById<View>(R.id.btn_save_treasury_position).setOnClickListener {
            val refStr = etRef.text.toString().trim()
            val principalDouble = etPrincipal.text.toString().trim().toDoubleOrNull() ?: 0.0
            val rateDouble = etRate.text.toString().trim().toDoubleOrNull() ?: -1.0
            val maturityDouble = etMaturity.text.toString().trim().toDoubleOrNull() ?: 0.0
            val liquidDouble = etLiquid.text.toString().trim().toDoubleOrNull() ?: 0.0
            val reasonStr = etReason.text.toString().trim()

            if (rateDouble < 0.0) {
                Toast.makeText(requireContext(), "FD Interest Rate is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (reasonStr.isBlank()) {
                Toast.makeText(requireContext(), "Update reason is required for the audit log", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newPosition = TreasuryPosition(
                bankName = "Punjab National Bank (PNB)",
                fdReferenceNumber = refStr,
                fdPrincipalPaise = Math.round(principalDouble * 100.0),
                fdInterestRateBps = Math.round(rateDouble * 100.0),
                fdMaturityValuePaise = Math.round(maturityDouble * 100.0),
                liquidBankReservePaise = Math.round(liquidDouble * 100.0),
                lastUpdated = System.currentTimeMillis(),
                updatedBy = AdminProfileRepository.getProfile().adminName,
                documentUrl = dialogUploadedDocMeta?.url ?: currentTreasuryPosition.documentUrl,
                documentFileName = dialogUploadedDocMeta?.fileName ?: currentTreasuryPosition.documentFileName,
                documentStoragePath = dialogUploadedDocMeta?.storagePath ?: currentTreasuryPosition.documentStoragePath,
                documentUploadedBy = dialogUploadedDocMeta?.uploadedBy ?: currentTreasuryPosition.documentUploadedBy,
                documentUploadedAt = dialogUploadedDocMeta?.uploadedAt ?: currentTreasuryPosition.documentUploadedAt,
                documentSizeBytes = dialogUploadedDocMeta?.sizeBytes ?: currentTreasuryPosition.documentSizeBytes
            )

            viewLifecycleOwner.lifecycleScope.launch {
                val adminName = AdminProfileRepository.getProfile().adminName
                val adminId = AdminProfileRepository.getProfile().mobileNumber
                val ok = repository.recordTreasuryPosition(newPosition, reasonStr, adminName, adminId)
                if (ok) {
                    Toast.makeText(requireContext(), "Treasury position & audit log committed 🌸", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Failed to commit atomic treasury update.", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun viewConfirmationDocument() {
        val url = currentTreasuryPosition.documentUrl
        if (url.isBlank()) {
            Toast.makeText(requireContext(), "No confirmation document attached", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to open document: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptUploadConfirmationDocument(isReplacing: Boolean) {
        if (isReplacing) {
            val inputReason = EditText(requireContext()).apply {
                hint = "e.g. Attached renewed PNB e-FD certificate"
                setPadding(40, 30, 40, 30)
            }
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Replace Confirmation Document")
                .setMessage("A mandatory audit reason is required when replacing a PNB e-FD confirmation receipt.")
                .setView(inputReason)
                .setPositiveButton("Select Document") { _, _ ->
                    val reason = inputReason.text.toString().trim()
                    if (reason.isBlank()) {
                        Toast.makeText(requireContext(), "Audit reason is required to replace document", Toast.LENGTH_LONG).show()
                    } else {
                        launchDocumentPicker(reason)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            launchDocumentPicker("Attached initial PNB e-FD confirmation document")
        }
    }

    private fun launchDocumentPicker(auditReason: String) {
        onDocumentPickedCallback = { uri ->
            uploadAndCommitDocument(uri, auditReason)
        }
        try {
            pickDocumentLauncher.launch("*/*")
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error opening file picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadAndCommitDocument(uri: Uri, auditReason: String) {
        pbFdDocUpload.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resolver = requireContext().contentResolver
                val fileName = getFileNameFromUri(uri)
                val mimeType = resolver.getType(uri) ?: "application/pdf"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    Toast.makeText(requireContext(), "Selected file is empty", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val adminProfile = AdminProfileRepository.getProfile()
                val email = if (adminProfile.email.isNotBlank()) adminProfile.email else "admin@ourbloom.app"

                val uploadResult = repository.uploadFdConfirmationDocument(
                    fileBytes = bytes,
                    originalFileName = fileName,
                    mimeType = mimeType,
                    adminEmail = email
                )

                if (uploadResult.isSuccess) {
                    val meta = uploadResult.getOrThrow()
                    val ok = repository.updateTreasuryConfirmationDocument(
                        documentMeta = meta,
                        reason = auditReason,
                        adminName = adminProfile.adminName,
                        adminId = adminProfile.mobileNumber
                    )
                    if (ok) {
                        Toast.makeText(requireContext(), "✓ PNB e-FD confirmation uploaded & audited 🌸", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to record treasury audit trail", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Upload failed: ${uploadResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Upload error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                pbFdDocUpload.visibility = View.GONE
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "PNB_FD_Confirmation.pdf"
        try {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val str = it.getString(idx)
                        if (!str.isNullOrBlank()) name = str
                    }
                }
            }
        } catch (_: Exception) {}
        return name
    }

    private fun showRecordManualRevenueDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_record_revenue)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val rgType = dialog.findViewById<RadioGroup>(R.id.rg_revenue_type)
        val etTitle = dialog.findViewById<EditText>(R.id.et_revenue_title)
        val etGross = dialog.findViewById<EditText>(R.id.et_revenue_gross_amount)
        val etSellerCost = dialog.findViewById<EditText>(R.id.et_revenue_seller_cost)
        val etCoupleId = dialog.findViewById<EditText>(R.id.et_revenue_couple_id)
        val etReason = dialog.findViewById<EditText>(R.id.et_revenue_entry_reason)

        dialog.findViewById<View>(R.id.btn_close_revenue_dialog).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btn_cancel_manual_revenue).setOnClickListener { dialog.dismiss() }

        dialog.findViewById<View>(R.id.btn_submit_manual_revenue).setOnClickListener {
            val typeStr = when (rgType.checkedRadioButtonId) {
                R.id.rb_type_sub -> "SUBSCRIPTION"
                R.id.rb_type_fee -> "FEE"
                else -> "GIFT"
            }
            val titleStr = etTitle.text.toString().trim()
            val grossDouble = etGross.text.toString().trim().toDoubleOrNull() ?: 0.0
            val sellerCostDouble = etSellerCost.text.toString().trim().toDoubleOrNull() ?: 0.0
            val coupleIdStr = etCoupleId.text.toString().trim()
            val reasonStr = etReason.text.toString().trim()

            if (titleStr.isBlank() || grossDouble <= 0.0) {
                Toast.makeText(requireContext(), "Title and gross amount are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (reasonStr.isBlank()) {
                Toast.makeText(requireContext(), "Manual entry reason is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val grossPaise = Math.round(grossDouble * 100.0)
            val sellerCostPaise = Math.round(sellerCostDouble * 100.0)
            val netRevenuePaise = (grossPaise - sellerCostPaise).coerceAtLeast(0L)

            val newRecord = RevenueRecord(
                type = typeStr,
                title = titleStr,
                grossAmountPaise = grossPaise,
                sellerPayablePaise = sellerCostPaise,
                netRevenuePaise = netRevenuePaise,
                coupleId = coupleIdStr,
                paymentMethod = "Manual",
                manualEntryReason = reasonStr,
                createdBy = AdminProfileRepository.getProfile().adminName,
                timestamp = System.currentTimeMillis()
            )

            viewLifecycleOwner.lifecycleScope.launch {
                val ok = repository.recordManualRevenue(newRecord)
                if (ok) {
                    Toast.makeText(requireContext(), "Revenue receipt committed to canonical ledger 🌸", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Failed to record manual revenue.", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        revListener?.remove()
        posListener?.remove()
        auditListener?.remove()
        txListener?.remove()
        walletListener?.remove()
        withListener?.remove()
    }
}
