package com.ourbloom.admin.bugs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ourbloom.admin.R
import com.ourbloom.admin.util.ClipboardHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BugRadarAdapter : ListAdapter<DetectedBug, BugRadarAdapter.BugViewHolder>(BugDiffCallback()) {

    private val expandedBugIds = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BugViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detected_bug, parent, false)
        return BugViewHolder(view)
    }

    override fun onBindViewHolder(holder: BugViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BugViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSeverity = itemView.findViewById<TextView>(R.id.tv_bug_severity)
        private val tvTag = itemView.findViewById<TextView>(R.id.tv_bug_tag)
        private val tvTime = itemView.findViewById<TextView>(R.id.tv_bug_time)
        private val tvMessage = itemView.findViewById<TextView>(R.id.tv_bug_message)
        private val layoutToggle = itemView.findViewById<LinearLayout>(R.id.layout_toggle_details)
        private val tvToggleText = itemView.findViewById<TextView>(R.id.tv_toggle_details_text)
        private val layoutStack = itemView.findViewById<LinearLayout>(R.id.layout_stacktrace_container)
        private val tvStacktrace = itemView.findViewById<TextView>(R.id.tv_bug_stacktrace)
        private val btnCopyStack = itemView.findViewById<Button>(R.id.btn_copy_stack)

        fun bind(bug: DetectedBug) {
            tvTag.text = bug.tag
            tvMessage.text = bug.message

            // Timestamp
            val timeFormatter = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            tvTime.text = timeFormatter.format(Date(bug.timestamp))

            // Severity styling
            when (bug.severity) {
                BugSeverity.CRITICAL -> {
                    tvSeverity.text = if (bug.isFatal) "FATAL CRASH" else "CRITICAL"
                    tvSeverity.setBackgroundResource(R.drawable.bg_status_rejected)
                    tvSeverity.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_crimson))
                }
                BugSeverity.WARNING -> {
                    tvSeverity.text = "WARNING"
                    tvSeverity.setBackgroundResource(R.drawable.bg_status_pending)
                    tvSeverity.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_amber))
                }
                BugSeverity.NETWORK_ERROR -> {
                    tvSeverity.text = "NETWORK"
                    tvSeverity.setBackgroundResource(R.drawable.bg_input)
                    tvSeverity.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_blue))
                }
                BugSeverity.UI_ERROR -> {
                    tvSeverity.text = "UI ERROR"
                    tvSeverity.setBackgroundResource(R.drawable.bg_input)
                    tvSeverity.setTextColor(ContextCompat.getColor(itemView.context, R.color.admin_rose))
                }
            }

            // Stacktrace & Collapsible logic
            val hasDetails = !bug.technicalDetails.isNullOrBlank()
            if (hasDetails) {
                layoutToggle.visibility = View.VISIBLE
                tvStacktrace.text = bug.technicalDetails

                val isExpanded = expandedBugIds.contains(bug.id)
                layoutStack.visibility = if (isExpanded) View.VISIBLE else View.GONE
                tvToggleText.text = if (isExpanded) "▲ Hide Stacktrace" else "▼ Show Technical Stacktrace"

                layoutToggle.setOnClickListener {
                    if (expandedBugIds.contains(bug.id)) {
                        expandedBugIds.remove(bug.id)
                        layoutStack.visibility = View.GONE
                        tvToggleText.text = "▼ Show Technical Stacktrace"
                    } else {
                        expandedBugIds.add(bug.id)
                        layoutStack.visibility = View.VISIBLE
                        tvToggleText.text = "▲ Hide Stacktrace"
                    }
                }

                btnCopyStack.setOnClickListener {
                    ClipboardHelper.copyToClipboard(
                        itemView.context,
                        "Diagnostic Report",
                        bug.formatDiagnosticReport()
                    )
                }
            } else {
                layoutToggle.visibility = View.GONE
                layoutStack.visibility = View.GONE
            }
        }
    }

    private class BugDiffCallback : DiffUtil.ItemCallback<DetectedBug>() {
        override fun areItemsTheSame(oldItem: DetectedBug, newItem: DetectedBug): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DetectedBug, newItem: DetectedBug): Boolean =
            oldItem == newItem
    }
}
