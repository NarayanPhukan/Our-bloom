package com.ourbloom.admin.transactions

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.admin.R
import com.ourbloom.admin.data.AdminFirestoreRepository
import com.ourbloom.admin.data.models.SavingsTransaction
import kotlinx.coroutines.launch

class AdminTransactionsFragment : Fragment() {

    private val repository = AdminFirestoreRepository()
    private var listenerRegistration: ListenerRegistration? = null

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var fabManualCredit: ExtendedFloatingActionButton

    private var allTransactions: List<SavingsTransaction> = emptyList()
    private var currentTypeFilter: String = "ALL"
    private var currentSearchQuery: String = ""

    private val adapter = TransactionsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_admin_transactions, container, false)
        initViews(root)
        setupListener()
        return root
    }

    private fun initViews(root: View) {
        swipeRefresh = root.findViewById(R.id.swipe_refresh_ledger)
        recyclerView = root.findViewById(R.id.recycler_ledger)
        tvEmpty = root.findViewById(R.id.tv_empty_ledger)
        etSearch = root.findViewById(R.id.et_search_ledger)
        chipGroup = root.findViewById(R.id.chip_group_tx_filter)
        fabManualCredit = root.findViewById(R.id.fab_manual_credit)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            swipeRefresh.isRefreshing = false
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentTypeFilter = when {
                checkedIds.contains(R.id.chip_tx_deposits) -> "DEPOSITS"
                checkedIds.contains(R.id.chip_tx_withdrawals) -> "WITHDRAWALS"
                else -> "ALL"
            }
            applyFilters()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        fabManualCredit.setOnClickListener {
            ManualCreditDialog(requireContext()) { coupleId, amount, utr, note ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = repository.manualCreditDeposit(coupleId, amount, utr, note)
                    if (success) {
                        Toast.makeText(requireContext(), "₹${amount.toInt()} successfully credited to vault $coupleId! 🌸", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to credit vault. Check logs.", Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
        }
    }

    private fun setupListener() {
        listenerRegistration = repository.observeTransactions { list ->
            if (!isAdded) return@observeTransactions
            allTransactions = list
            applyFilters()
        }
    }

    private fun applyFilters() {
        var filtered = allTransactions

        if (currentTypeFilter == "DEPOSITS") {
            filtered = filtered.filter { it.type.equals("deposit", ignoreCase = true) }
        } else if (currentTypeFilter == "WITHDRAWALS") {
            filtered = filtered.filter { !it.type.equals("deposit", ignoreCase = true) }
        }

        if (currentSearchQuery.isNotBlank()) {
            val q = currentSearchQuery.lowercase()
            filtered = filtered.filter {
                it.utrNumber.lowercase().contains(q) ||
                it.userName.lowercase().contains(q) ||
                it.coupleId.lowercase().contains(q) ||
                it.note.lowercase().contains(q)
            }
        }

        adapter.submitList(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
    }
}
