package com.ourbloom.admin.bugs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ourbloom.admin.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BugRadarDialog(context: Context) : Dialog(context) {

    private val adapter = BugRadarAdapter()
    private var observeJob: Job? = null

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_bug_radar)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.94).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvCountBadge = findViewById<TextView>(R.id.tv_bug_count_badge)
        val rvBugs = findViewById<RecyclerView>(R.id.rv_bugs)
        val layoutNoBugs = findViewById<LinearLayout>(R.id.layout_no_bugs)
        val btnSimulate = findViewById<Button>(R.id.btn_simulate_bug)
        val btnClear = findViewById<Button>(R.id.btn_clear_bugs)
        val btnClose = findViewById<Button>(R.id.btn_close_bug_radar)

        rvBugs.layoutManager = LinearLayoutManager(context)
        rvBugs.adapter = adapter

        // Setup actions
        btnSimulate.setOnClickListener {
            AdminBugRadar.simulateTestError(context)
        }

        btnClear.setOnClickListener {
            AdminBugRadar.clearAllBugs()
        }

        btnClose.setOnClickListener {
            dismiss()
        }

        // Live observation of detected bugs
        if (context is LifecycleOwner) {
            observeJob = context.lifecycleScope.launch {
                AdminBugRadar.bugsState.collectLatest { bugs ->
                    updateBugList(bugs, tvCountBadge, rvBugs, layoutNoBugs)
                }
            }
        } else {
            // Direct initial load
            updateBugList(AdminBugRadar.bugsState.value, tvCountBadge, rvBugs, layoutNoBugs)
        }
    }

    private fun updateBugList(
        bugs: List<DetectedBug>,
        tvBadge: TextView,
        rvBugs: RecyclerView,
        layoutEmpty: LinearLayout
    ) {
        tvBadge.text = "${bugs.size} Events"
        if (bugs.isEmpty()) {
            rvBugs.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
            tvBadge.setBackgroundResource(R.drawable.bg_status_verified)
            tvBadge.setTextColor(context.getColor(R.color.admin_emerald))
        } else {
            rvBugs.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
            val hasCritical = bugs.any { it.severity == BugSeverity.CRITICAL }
            if (hasCritical) {
                tvBadge.setBackgroundResource(R.drawable.bg_status_rejected)
                tvBadge.setTextColor(context.getColor(R.color.admin_crimson))
            } else {
                tvBadge.setBackgroundResource(R.drawable.bg_status_pending)
                tvBadge.setTextColor(context.getColor(R.color.admin_amber))
            }
            adapter.submitList(bugs)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        observeJob?.cancel()
    }
}
