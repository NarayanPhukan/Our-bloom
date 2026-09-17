package com.ourbloom.admin.wallets

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
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.admin.R
import com.ourbloom.admin.data.AdminFirestoreRepository
import com.ourbloom.admin.data.models.SavingsWallet
import kotlinx.coroutines.launch

class AdminWalletsFragment : Fragment() {

    private val repository = AdminFirestoreRepository()
    private var listenerRegistration: ListenerRegistration? = null

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText

    private var allWallets: List<SavingsWallet> = emptyList()
    private var searchQuery: String = ""

    private val adapter = WalletsAdapter(
        onAdjustClick = { wallet -> handleAdjustWallet(wallet) },
        onWalletClick = { wallet -> openCoupleDetail(wallet) }
    )

    private fun openCoupleDetail(wallet: SavingsWallet) {
        CoupleDetailDialog(
            requireContext(),
            wallet,
            repository,
            viewLifecycleOwner.lifecycleScope
        ).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_admin_wallets, container, false)
        initViews(root)
        setupListener()
        return root
    }

    private fun initViews(root: View) {
        swipeRefresh = root.findViewById(R.id.swipe_refresh_wallets)
        recyclerView = root.findViewById(R.id.recycler_wallets)
        tvEmpty = root.findViewById(R.id.tv_empty_wallets)
        etSearch = root.findViewById(R.id.et_search_wallets)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            swipeRefresh.isRefreshing = false
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupListener() {
        listenerRegistration = repository.observeSavingsWallets { list ->
            if (!isAdded) return@observeSavingsWallets
            allWallets = list
            applyFilter()
        }
    }

    private fun applyFilter() {
        val filtered = if (searchQuery.isBlank()) {
            allWallets
        } else {
            val q = searchQuery.lowercase()
            allWallets.filter {
                it.coupleId.lowercase().contains(q) ||
                it.user1Name.lowercase().contains(q) ||
                it.user2Name.lowercase().contains(q)
            }
        }

        adapter.submitList(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun handleAdjustWallet(wallet: SavingsWallet) {
        AdjustBalanceDialog(requireContext(), wallet) { newBalance, reason ->
            viewLifecycleOwner.lifecycleScope.launch {
                val success = repository.adjustWalletBalance(wallet.coupleId, newBalance, reason)
                if (success) {
                    Toast.makeText(requireContext(), "Wallet balance updated to ₹${newBalance.toInt()} 🌸", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to adjust balance. Check logs.", Toast.LENGTH_LONG).show()
                }
            }
        }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
    }
}
