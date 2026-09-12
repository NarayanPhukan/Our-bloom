package com.ourbloom.app.dashboard

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URLEncoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class SavingsVaultFragment : Fragment() {

    private val repository = FirestoreRepository()
    private var coupleId: String = ""
    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var currentUserEmail: String = ""
    private var currentUserPhone: String = ""

    private var currentWallet: SavingsWallet? = null
    private var currentGoals: List<SavingsGoal> = emptyList()
    private var activeRequest: WithdrawalRequest? = null

    private var walletListener: ListenerRegistration? = null
    private var goalsListener: ListenerRegistration? = null
    private var txnsListener: ListenerRegistration? = null
    private var withReqsListener: ListenerRegistration? = null

    private lateinit var goalsAdapter: SavingsGoalsAdapter
    private lateinit var txnsAdapter: SavingsTransactionsAdapter

    // Views
    private lateinit var tvTotalBalance: TextView
    private lateinit var tvLockStatus: TextView
    private lateinit var tvUser1Share: TextView
    private lateinit var tvUser2Share: TextView
    private lateinit var progressPartnerSplit: ProgressBar
    private lateinit var tvEmptyGoals: TextView
    private lateinit var tvEmptyTxns: TextView

    // Active withdrawal card views
    private lateinit var cardActiveWithdrawal: View
    private lateinit var tvActiveWithTitle: TextView
    private lateinit var tvActiveWithDetails: TextView
    private lateinit var tvActiveWithCountdown: TextView
    private lateinit var layoutWithApprovalButtons: View
    private lateinit var btnApproveWithdrawal: MaterialButton
    private lateinit var btnDeclineWithdrawal: MaterialButton
    private lateinit var btnCancelWithdrawal: MaterialButton

    // Cooldown ticker
    private val timerHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_savings_vault, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupAdapters(view)

        view.findViewById<ImageButton>(R.id.btn_back_vault).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<ImageButton>(R.id.btn_lock_date_settings).setOnClickListener {
            showSetLockDateDialog()
        }

        view.findViewById<View>(R.id.layout_lock_badge).setOnClickListener {
            showSetLockDateDialog()
        }

        view.findViewById<MaterialButton>(R.id.btn_action_contribute).setOnClickListener {
            showDepositBottomSheet()
        }

        view.findViewById<MaterialButton>(R.id.btn_action_withdraw).setOnClickListener {
            showWithdrawBottomSheet()
        }

        view.findViewById<MaterialButton>(R.id.btn_action_add_goal).setOnClickListener {
            showAddGoalDialog()
        }

        view.findViewById<TextView>(R.id.tv_add_goal_text).setOnClickListener {
            showAddGoalDialog()
        }

        lifecycleScope.launch {
            val user = repository.getCurrentUser()
            if (user != null) {
                currentUserId = user.uid
                currentUserName = user.name
                currentUserEmail = user.email.ifBlank {
                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""
                }
                currentUserPhone = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.phoneNumber ?: ""
                coupleId = user.coupleId ?: ""
                if (coupleId.isNotBlank()) {
                    startObserving()
                } else {
                    Toast.makeText(requireContext(), "Couple profile not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun initViews(view: View) {
        tvTotalBalance = view.findViewById(R.id.tv_vault_total_balance)
        tvLockStatus = view.findViewById(R.id.tv_lock_status)
        tvUser1Share = view.findViewById(R.id.tv_user1_share)
        tvUser2Share = view.findViewById(R.id.tv_user2_share)
        progressPartnerSplit = view.findViewById(R.id.progress_partner_split)
        tvEmptyGoals = view.findViewById(R.id.tv_empty_goals)
        tvEmptyTxns = view.findViewById(R.id.tv_empty_transactions)

        cardActiveWithdrawal = view.findViewById(R.id.card_active_withdrawal)
        tvActiveWithTitle = view.findViewById(R.id.tv_active_with_title)
        tvActiveWithDetails = view.findViewById(R.id.tv_active_with_details)
        tvActiveWithCountdown = view.findViewById(R.id.tv_active_with_countdown)
        layoutWithApprovalButtons = view.findViewById(R.id.layout_with_approval_buttons)
        btnApproveWithdrawal = view.findViewById(R.id.btn_approve_withdrawal)
        btnDeclineWithdrawal = view.findViewById(R.id.btn_decline_withdrawal)
        btnCancelWithdrawal = view.findViewById(R.id.btn_cancel_withdrawal)
    }

    private fun setupAdapters(view: View) {
        val rvGoals = view.findViewById<RecyclerView>(R.id.rv_savings_goals)
        rvGoals.layoutManager = LinearLayoutManager(requireContext())
        goalsAdapter = SavingsGoalsAdapter(
            onQuickAddClick = { goal ->
                showDepositBottomSheet(goal)
            },
            onDeleteClick = { goal ->
                confirmDeleteGoal(goal)
            }
        )
        rvGoals.adapter = goalsAdapter

        val rvTxns = view.findViewById<RecyclerView>(R.id.rv_savings_transactions)
        rvTxns.layoutManager = LinearLayoutManager(requireContext())
        txnsAdapter = SavingsTransactionsAdapter()
        rvTxns.adapter = txnsAdapter
    }

    private fun startObserving() {
        walletListener?.remove()
        walletListener = repository.observeSavingsWallet(coupleId) { wallet ->
            if (!isAdded) return@observeSavingsWallet
            currentWallet = wallet
            updateWalletUI(wallet)
        }

        goalsListener?.remove()
        goalsListener = repository.observeSavingsGoals(coupleId) { goals ->
            if (!isAdded) return@observeSavingsGoals
            currentGoals = goals
            goalsAdapter.submitList(goals)
            tvEmptyGoals.visibility = if (goals.isEmpty()) View.VISIBLE else View.GONE
        }

        txnsListener?.remove()
        txnsListener = repository.observeSavingsTransactions(coupleId) { txns ->
            if (!isAdded) return@observeSavingsTransactions
            txnsAdapter.submitList(txns)
            tvEmptyTxns.visibility = if (txns.isEmpty()) View.VISIBLE else View.GONE
        }

        withReqsListener?.remove()
        withReqsListener = repository.observeWithdrawalRequests(coupleId) { requests ->
            if (!isAdded) return@observeWithdrawalRequests
            val ongoing = requests.firstOrNull {
                it.status == "PENDING_APPROVAL" || it.status == "WAITING_PERIOD" || it.status == "PROCESSING_PAYOUT"
            }
            activeRequest = ongoing
            updateActiveWithdrawalUI(ongoing)
        }
    }

    private fun updateWalletUI(wallet: SavingsWallet?) {
        if (wallet == null) {
            tvTotalBalance.text = "₹0"
            tvUser1Share.text = "You: ₹0 (50%)"
            tvUser2Share.text = "Partner: ₹0 (50%)"
            progressPartnerSplit.progress = 50
            tvLockStatus.text = "Set lock date"
            return
        }

        val total = wallet.totalBalance
        val cleanTotal = if (total % 1.0 == 0.0) total.toInt().toString() else String.format(Locale.US, "%.2f", total)
        tvTotalBalance.text = "₹$cleanTotal"

        val u1 = wallet.user1Total
        val u2 = wallet.user2Total
        val isMeUser1 = currentUserId == wallet.user1Id || wallet.user1Id.isBlank()

        val myTotal = if (isMeUser1) u1 else u2
        val partnerTotal = if (isMeUser1) u2 else u1

        val myPercent = if (total > 0) ((myTotal / total) * 100).toInt().coerceIn(0, 100) else 50
        val partnerPercent = if (total > 0) (100 - myPercent) else 50

        tvUser1Share.text = "You: ₹${myTotal.toInt()} ($myPercent%)"
        tvUser2Share.text = "Partner: ₹${partnerTotal.toInt()} ($partnerPercent%)"
        progressPartnerSplit.progress = myPercent

        // Lock Date calculation
        val lockDate = wallet.lockUntilDate
        val now = System.currentTimeMillis()
        if (lockDate > 0L) {
            val diff = lockDate - now
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val dateStr = sdf.format(Date(lockDate))
            if (diff > 0) {
                val daysLeft = TimeUnit.MILLISECONDS.toDays(diff)
                tvLockStatus.text = "Locked until $dateStr (${daysLeft}d left)"
            } else {
                tvLockStatus.text = "Matured on $dateStr (Ready)"
            }
        } else {
            tvLockStatus.text = "Tap to set lock date"
        }
    }

    private fun updateActiveWithdrawalUI(req: WithdrawalRequest?) {
        stopCountdownTimer()

        if (req == null) {
            cardActiveWithdrawal.visibility = View.GONE
            return
        }

        cardActiveWithdrawal.visibility = View.VISIBLE
        val isRequester = req.requestedByUid == currentUserId
        val cleanAmount = if (req.amount % 1.0 == 0.0) req.amount.toInt().toString() else String.format(Locale.US, "%.2f", req.amount)

        when (req.status) {
            "PENDING_APPROVAL" -> {
                tvActiveWithCountdown.visibility = View.GONE
                if (!isRequester) {
                    tvActiveWithTitle.text = "⚠️ Withdrawal Request from ${req.requestedByName}"
                    tvActiveWithDetails.text = "₹$cleanAmount requested for \"${req.reason}\". Mode: ${req.payoutMode}. Please review destination accounts."
                    layoutWithApprovalButtons.visibility = View.VISIBLE
                    btnCancelWithdrawal.visibility = View.GONE

                    btnApproveWithdrawal.setOnClickListener {
                        confirmAndApproveWithdrawal(req)
                    }
                    btnDeclineWithdrawal.setOnClickListener {
                        confirmAndDeclineWithdrawal(req)
                    }
                } else {
                    tvActiveWithTitle.text = "⏳ Withdrawal Pending Partner Approval"
                    tvActiveWithDetails.text = "₹$cleanAmount requested for \"${req.reason}\". Waiting for your partner's approval."
                    layoutWithApprovalButtons.visibility = View.GONE
                    btnCancelWithdrawal.visibility = View.VISIBLE
                    btnCancelWithdrawal.text = "Cancel Request"
                    btnCancelWithdrawal.setOnClickListener {
                        cancelWithdrawal(req)
                    }
                }
            }
            "WAITING_PERIOD" -> {
                layoutWithApprovalButtons.visibility = View.GONE
                btnCancelWithdrawal.visibility = View.VISIBLE
                btnCancelWithdrawal.text = "Cancel Withdrawal"
                btnCancelWithdrawal.setOnClickListener {
                    cancelWithdrawal(req)
                }

                tvActiveWithTitle.text = "⏳ 4-Day Emergency Cooldown Active"
                tvActiveWithDetails.text = "₹$cleanAmount approved for \"${req.reason}\". Cooling period active before payout processing."
                tvActiveWithCountdown.visibility = View.VISIBLE

                startCountdownTimer(req)
            }
            "PROCESSING_PAYOUT" -> {
                layoutWithApprovalButtons.visibility = View.GONE
                btnCancelWithdrawal.visibility = View.GONE
                tvActiveWithCountdown.visibility = View.GONE

                tvActiveWithTitle.text = "Processing Payout ⏳"
                tvActiveWithDetails.text = "₹$cleanAmount approved! Amount will be credited to your bank account within 48 hours."
            }
            else -> {
                cardActiveWithdrawal.visibility = View.GONE
            }
        }
    }

    private fun startCountdownTimer(req: WithdrawalRequest) {
        val target = req.waitingPeriodEndsAt ?: return
        countdownRunnable = object : Runnable {
            override fun run() {
                val remaining = target - System.currentTimeMillis()
                if (remaining <= 0) {
                    tvActiveWithCountdown.text = "Cooldown complete. Moving to payout..."
                    lifecycleScope.launch {
                        repository.advanceCooldownToProcessing(req.id, req)
                    }
                } else {
                    val days = TimeUnit.MILLISECONDS.toDays(remaining)
                    val hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                    tvActiveWithCountdown.text = "⏳ Cooldown: ${days}d ${hours}h ${minutes}m ${seconds}s remaining"
                    timerHandler.postDelayed(this, 1000)
                }
            }
        }
        countdownRunnable?.run()
    }

    private fun stopCountdownTimer() {
        countdownRunnable?.let { timerHandler.removeCallbacks(it) }
        countdownRunnable = null
    }

    // ==========================================
    // DEPOSIT FLOW
    // ==========================================

    private fun showDepositBottomSheet(preselectedGoal: SavingsGoal? = null) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_deposit_savings, null)
        dialog.setContentView(sheetView)

        val etAmount = sheetView.findViewById<TextInputEditText>(R.id.et_deposit_amount)
        val etNote = sheetView.findViewById<TextInputEditText>(R.id.et_deposit_note)
        val spinnerGoal = sheetView.findViewById<Spinner>(R.id.spinner_deposit_goal)
        val btnPayPayu = sheetView.findViewById<MaterialButton>(R.id.btn_pay_payu)

        sheetView.findViewById<View>(R.id.btn_close_deposit).setOnClickListener {
            dialog.dismiss()
        }

        // Setup goals dropdown
        val goalOptions = mutableListOf("General Vault (No specific goal)")
        val goalsMap = mutableMapOf<Int, SavingsGoal?>()
        goalsMap[0] = null

        currentGoals.forEachIndexed { index, goal ->
            goalOptions.add("🎯 ${goal.title} (₹${goal.currentAmount.toInt()} / ₹${goal.targetAmount.toInt()})")
            goalsMap[index + 1] = goal
        }

        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, goalOptions)
        spinnerGoal.adapter = spinnerAdapter

        if (preselectedGoal != null) {
            val idx = currentGoals.indexOfFirst { it.id == preselectedGoal.id }
            if (idx >= 0) {
                spinnerGoal.setSelection(idx + 1)
            }
        }

        // Quick amount chips
        val chipGroup = sheetView.findViewById<ChipGroup>(R.id.chip_group_quick_amounts)
        sheetView.findViewById<Chip>(R.id.chip_100).setOnClickListener { etAmount.setText("100") }
        sheetView.findViewById<Chip>(R.id.chip_500).setOnClickListener { etAmount.setText("500") }
        sheetView.findViewById<Chip>(R.id.chip_1000).setOnClickListener { etAmount.setText("1000") }
        sheetView.findViewById<Chip>(R.id.chip_2000).setOnClickListener { etAmount.setText("2000") }
        sheetView.findViewById<Chip>(R.id.chip_5000).setOnClickListener { etAmount.setText("5000") }

        // Pay via PayU Hosted Gateway
        btnPayPayu.setOnClickListener {
            val amountStr = etAmount.text.toString().trim()
            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0.0) {
                Toast.makeText(requireContext(), "Please enter a valid deposit amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val note = etNote.text.toString().trim()
            val selectedGoal = goalsMap[spinnerGoal.selectedItemPosition]

            launchPayUCheckout(amount, note, selectedGoal?.id, selectedGoal?.title)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun launchPayUCheckout(
        amount: Double,
        note: String,
        goalId: String?,
        goalTitle: String?
    ) {
        try {
            val baseUrl = "https://our-bloom.onrender.com"
            val encodedName = URLEncoder.encode(currentUserName.ifBlank { "Partner" }, "UTF-8")
            val encodedEmail = URLEncoder.encode(currentUserEmail.ifBlank { "support@ourbloom.app" }, "UTF-8")
            val encodedPhone = URLEncoder.encode(currentUserPhone.ifBlank { "9999999999" }, "UTF-8")
            val encodedNote = URLEncoder.encode(note.ifBlank { "Couple Vault Deposit" }, "UTF-8")
            val encodedGoalTitle = URLEncoder.encode(goalTitle ?: "", "UTF-8")
            val goalIdParam = goalId ?: ""

            val checkoutUrl = "$baseUrl/api/payu/checkout?" +
                    "amount=$amount" +
                    "&coupleId=$coupleId" +
                    "&userId=$currentUserId" +
                    "&userName=$encodedName" +
                    "&userEmail=$encodedEmail" +
                    "&userPhone=$encodedPhone" +
                    "&note=$encodedNote" +
                    "&goalId=$goalIdParam" +
                    "&goalTitle=$encodedGoalTitle"

            Toast.makeText(requireContext(), "Opening PayU Gateway... 🌸", Toast.LENGTH_SHORT).show()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(requireContext(), Uri.parse(checkoutUrl))
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://our-bloom.onrender.com/api/payu/checkout?amount=$amount&coupleId=$coupleId"))
                startActivity(browserIntent)
            } catch (err: Exception) {
                Toast.makeText(requireContext(), "Unable to open browser: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==========================================
    // WITHDRAWAL FLOW
    // ==========================================

    private fun showWithdrawBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_withdraw_savings, null)
        dialog.setContentView(sheetView)

        val etAmount = sheetView.findViewById<TextInputEditText>(R.id.et_withdraw_amount)
        val etReason = sheetView.findViewById<TextInputEditText>(R.id.et_withdraw_reason)
        val cardBanner = sheetView.findViewById<View>(R.id.card_withdraw_status_banner)
        val tvBannerDesc = sheetView.findViewById<TextView>(R.id.tv_withdraw_banner_desc)
        val rgMode = sheetView.findViewById<RadioGroup>(R.id.rg_payout_mode)
        val layoutJoint = sheetView.findViewById<View>(R.id.layout_mode_joint)
        val layoutSeparated = sheetView.findViewById<View>(R.id.layout_mode_separated)

        sheetView.findViewById<View>(R.id.btn_close_withdraw).setOnClickListener {
            dialog.dismiss()
        }

        // Check lock status
        val lockDate = currentWallet?.lockUntilDate ?: 0L
        val now = System.currentTimeMillis()
        val isEmergency = lockDate > 0L && now < lockDate

        if (!isEmergency) {
            tvBannerDesc.text = "Maturity withdrawal requires mutual partner consent and bank details."
        }

        // Toggle mode containers
        rgMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_mode_joint) {
                layoutJoint.visibility = View.VISIBLE
                layoutSeparated.visibility = View.GONE
            } else {
                layoutJoint.visibility = View.GONE
                layoutSeparated.visibility = View.VISIBLE
                calculateSeparatedShares(sheetView, etAmount.text.toString().toDoubleOrNull() ?: 0.0)
            }
        }

        etAmount.setOnFocusChangeListener { _, _ ->
            if (rgMode.checkedRadioButtonId == R.id.rb_mode_separated) {
                calculateSeparatedShares(sheetView, etAmount.text.toString().toDoubleOrNull() ?: 0.0)
            }
        }

        val btnSubmit = sheetView.findViewById<MaterialButton>(R.id.btn_submit_withdraw)
        btnSubmit.setOnClickListener {
            val amount = etAmount.text.toString().toDoubleOrNull()
            val totalBalance = currentWallet?.totalBalance ?: 0.0

            if (amount == null || amount <= 0.0) {
                Toast.makeText(requireContext(), "Please enter a valid withdrawal amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (amount > totalBalance) {
                Toast.makeText(requireContext(), "Amount exceeds available vault balance (₹${totalBalance.toInt()})", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val reason = etReason.text.toString().trim()
            if (reason.isBlank()) {
                Toast.makeText(requireContext(), "Please state the reason for withdrawal", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isJoint = rgMode.checkedRadioButtonId == R.id.rb_mode_joint
            var jointAccount: BankAccountDetails? = null
            var p1Account: BankAccountDetails? = null
            var p2Account: BankAccountDetails? = null
            var p1Share = 0.0
            var p2Share = 0.0

            if (isJoint) {
                val acc = sheetView.findViewById<TextInputEditText>(R.id.et_joint_acc_num).text.toString().trim()
                val ifsc = sheetView.findViewById<TextInputEditText>(R.id.et_joint_ifsc).text.toString().trim()
                val holder = sheetView.findViewById<TextInputEditText>(R.id.et_joint_holder_name).text.toString().trim()

                if (acc.length < 6 || ifsc.length < 4 || holder.isBlank()) {
                    Toast.makeText(requireContext(), "Please enter complete Joint Account bank details", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                jointAccount = BankAccountDetails(accountNumber = acc, ifscCode = ifsc, accountHolderName = holder)
            } else {
                val acc1 = sheetView.findViewById<TextInputEditText>(R.id.et_p1_acc_num).text.toString().trim()
                val ifsc1 = sheetView.findViewById<TextInputEditText>(R.id.et_p1_ifsc).text.toString().trim()
                val h1 = sheetView.findViewById<TextInputEditText>(R.id.et_p1_holder).text.toString().trim()

                val acc2 = sheetView.findViewById<TextInputEditText>(R.id.et_p2_acc_num).text.toString().trim()
                val ifsc2 = sheetView.findViewById<TextInputEditText>(R.id.et_p2_ifsc).text.toString().trim()
                val h2 = sheetView.findViewById<TextInputEditText>(R.id.et_p2_holder).text.toString().trim()

                if (acc1.length < 6 || ifsc1.length < 4 || h1.isBlank() || acc2.length < 6 || ifsc2.length < 4 || h2.isBlank()) {
                    Toast.makeText(requireContext(), "Please enter complete bank details for both partners", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val u1Total = currentWallet?.user1Total ?: 0.0
                val ratio = if (totalBalance > 0) u1Total / totalBalance else 0.5
                p1Share = amount * ratio
                p2Share = amount - p1Share

                p1Account = BankAccountDetails(accountNumber = acc1, ifscCode = ifsc1, accountHolderName = h1)
                p2Account = BankAccountDetails(accountNumber = acc2, ifscCode = ifsc2, accountHolderName = h2)
            }

            lifecycleScope.launch {
                val success = repository.submitWithdrawalRequest(
                    context = requireContext(),
                    coupleId = coupleId,
                    amount = amount,
                    reason = reason,
                    payoutMode = if (isJoint) "JOINT" else "SEPARATED",
                    jointAccount = jointAccount,
                    p1Account = p1Account,
                    p1Share = p1Share,
                    p2Account = p2Account,
                    p2Share = p2Share
                )

                if (success) {
                    Toast.makeText(requireContext(), "Withdrawal request submitted for partner approval ✓", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Failed to submit withdrawal request", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun calculateSeparatedShares(sheetView: View, amount: Double) {
        val totalBalance = currentWallet?.totalBalance ?: 0.0
        val u1Total = currentWallet?.user1Total ?: 0.0
        val ratio = if (totalBalance > 0) u1Total / totalBalance else 0.5

        val p1Share = amount * ratio
        val p2Share = amount - p1Share

        sheetView.findViewById<TextView>(R.id.tv_sep_p1_title)?.text = "Partner 1 Account (Share: ₹${p1Share.toInt()})"
        sheetView.findViewById<TextView>(R.id.tv_sep_p2_title)?.text = "Partner 2 Account (Share: ₹${p2Share.toInt()})"
    }

    private fun confirmAndApproveWithdrawal(req: WithdrawalRequest) {
        AlertDialog.Builder(requireContext())
            .setTitle("Approve Withdrawal Request?")
            .setMessage("Do you approve withdrawing ₹${req.amount.toInt()} for \"${req.reason}\"?\n\nDestination: ${req.payoutMode} bank account(s).\n\nUpon approval, payout will be processed within 48 hours${if (req.isEmergency) " after the 4-day cooldown" else ""}.")
            .setPositiveButton("Approve ✓") { _, _ ->
                lifecycleScope.launch {
                    val success = repository.approveWithdrawalRequest(requireContext(), req.id, req)
                    if (success) {
                        Toast.makeText(requireContext(), "Withdrawal Approved ✓", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmAndDeclineWithdrawal(req: WithdrawalRequest) {
        AlertDialog.Builder(requireContext())
            .setTitle("Decline Withdrawal?")
            .setMessage("Are you sure you want to decline this withdrawal request of ₹${req.amount.toInt()}?")
            .setPositiveButton("Decline Request") { _, _ ->
                lifecycleScope.launch {
                    repository.cancelOrRejectWithdrawalRequest(requireContext(), req.id, isReject = true, req)
                    Toast.makeText(requireContext(), "Request Declined", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun cancelWithdrawal(req: WithdrawalRequest) {
        AlertDialog.Builder(requireContext())
            .setTitle("Cancel Withdrawal?")
            .setMessage("Are you sure you want to cancel this withdrawal and keep the money saved in your vault?")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                lifecycleScope.launch {
                    repository.cancelOrRejectWithdrawalRequest(requireContext(), req.id, isReject = false, req)
                    Toast.makeText(requireContext(), "Withdrawal Cancelled", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    // ==========================================
    // GOALS & LOCK DATE
    // ==========================================

    private fun showAddGoalDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_savings_goal, null)
        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.et_goal_title)
        val etTarget = dialogView.findViewById<TextInputEditText>(R.id.et_goal_target_amount)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chip_group_goal_icons)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Create Goal") { _, _ ->
                val title = etTitle.text.toString().trim()
                val target = etTarget.text.toString().toDoubleOrNull() ?: 0.0
                if (title.isBlank() || target <= 0.0) {
                    Toast.makeText(requireContext(), "Please enter a valid goal title and target amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val icon = when (chipGroup.checkedChipId) {
                    R.id.chip_icon_wedding -> "wedding"
                    R.id.chip_icon_vacation -> "trip"
                    R.id.chip_icon_home -> "home"
                    R.id.chip_icon_car -> "car"
                    R.id.chip_icon_celebration -> "celebration"
                    else -> "savings"
                }

                lifecycleScope.launch {
                    val success = repository.createSavingsGoal(coupleId, title, target, icon)
                    if (success) {
                        Toast.makeText(requireContext(), "Goal \"$title\" created! 🎯", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteGoal(goal: SavingsGoal) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Goal?")
            .setMessage("Are you sure you want to delete \"${goal.title}\"? The savings already accumulated in your vault will remain safe.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteSavingsGoal(goal.id)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSetLockDateDialog() {
        val cal = Calendar.getInstance()
        val currentLock = currentWallet?.lockUntilDate ?: 0L
        if (currentLock > 0L) {
            cal.timeInMillis = currentLock
        } else {
            cal.add(Calendar.MONTH, 6) // default to 6 months from now
        }

        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 23, 59, 59)
                }
                lifecycleScope.launch {
                    val success = repository.setLockUntilDate(coupleId, chosen.timeInMillis)
                    if (success) {
                        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        Toast.makeText(requireContext(), "Vault locked until ${sdf.format(chosen.time)} 🔒", Toast.LENGTH_LONG).show()
                    }
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.datePicker.minDate = System.currentTimeMillis() + (24 * 60 * 60 * 1000L) // at least tomorrow
        datePicker.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopCountdownTimer()
        walletListener?.remove()
        goalsListener?.remove()
        txnsListener?.remove()
        withReqsListener?.remove()
    }
}
