package com.ourbloom.app.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ourbloom.app.R
import com.ourbloom.app.data.models.Milestone

class MilestoneAdapter : ListAdapter<Milestone, MilestoneAdapter.MilestoneViewHolder>(MilestoneDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MilestoneViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_timeline, parent, false)
        return MilestoneViewHolder(view)
    }

    override fun onBindViewHolder(holder: MilestoneViewHolder, position: Int) {
        val milestone = getItem(position)
        holder.bind(milestone)
    }

    class MilestoneViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        private val tvDescription: TextView = itemView.findViewById(R.id.tv_description)

        fun bind(milestone: Milestone) {
            tvDate.text = "Day ${milestone.day}" // TODO: format nice date if possible, but schema just has day (int)
            tvTitle.text = milestone.title
            tvDescription.text = milestone.body
        }
    }

    class MilestoneDiffCallback : DiffUtil.ItemCallback<Milestone>() {
        override fun areItemsTheSame(oldItem: Milestone, newItem: Milestone): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Milestone, newItem: Milestone): Boolean {
            return oldItem == newItem
        }
    }
}
