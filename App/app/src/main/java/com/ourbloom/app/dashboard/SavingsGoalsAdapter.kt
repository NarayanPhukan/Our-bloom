package com.ourbloom.app.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.ourbloom.app.R
import com.ourbloom.app.data.models.SavingsGoal
import java.util.Locale

class SavingsGoalsAdapter(
    private val onQuickAddClick: (SavingsGoal) -> Unit,
    private val onDeleteClick: (SavingsGoal) -> Unit
) : RecyclerView.Adapter<SavingsGoalsAdapter.GoalViewHolder>() {

    private val items = mutableListOf<SavingsGoal>()

    fun submitList(newItems: List<SavingsGoal>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GoalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_savings_goal, parent, false)
        return GoalViewHolder(view)
    }

    override fun onBindViewHolder(holder: GoalViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class GoalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIcon = itemView.findViewById<TextView>(R.id.tv_goal_icon)
        private val tvTitle = itemView.findViewById<TextView>(R.id.tv_goal_title)
        private val tvCategory = itemView.findViewById<TextView>(R.id.tv_goal_category)
        private val tvCompletedBadge = itemView.findViewById<TextView>(R.id.tv_goal_completed_badge)
        private val btnDelete = itemView.findViewById<ImageButton>(R.id.btn_delete_goal)
        private val progressGoal = itemView.findViewById<LinearProgressIndicator>(R.id.progress_goal)
        private val tvAmounts = itemView.findViewById<TextView>(R.id.tv_goal_amounts)
        private val btnQuickAdd = itemView.findViewById<MaterialButton>(R.id.btn_quick_add_goal)

        fun bind(goal: SavingsGoal) {
            tvTitle.text = goal.title
            tvCategory.text = goal.category.ifBlank { "Savings Target" }

            tvIcon.text = when (goal.icon.lowercase(Locale.US)) {
                "wedding", "ring" -> "💍"
                "trip", "vacation", "flight" -> "✈️"
                "home", "house" -> "🏡"
                "car" -> "🚗"
                "celebration", "special" -> "🎉"
                else -> "🎯"
            }

            val target = goal.targetAmount
            val current = goal.currentAmount
            val percent = if (target > 0) ((current / target) * 100).toInt().coerceIn(0, 100) else 0

            progressGoal.progress = percent
            tvAmounts.text = "₹${current.toInt()} of ₹${target.toInt()} ($percent%)"

            if (percent >= 100 || goal.isCompleted) {
                tvCompletedBadge.visibility = View.VISIBLE
            } else {
                tvCompletedBadge.visibility = View.GONE
            }

            btnQuickAdd.setOnClickListener {
                onQuickAddClick(goal)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(goal)
            }
        }
    }
}
