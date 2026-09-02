package com.ourbloom.app.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ourbloom.app.R
import com.ourbloom.app.data.models.Memory

class MemoriesAdapter(
    private val onItemClick: (Memory) -> Unit,
    private val onDeleteClick: (Memory) -> Unit
) : RecyclerView.Adapter<MemoriesAdapter.MemoryViewHolder>() {

    private var memories: List<Memory> = emptyList()

    fun submitList(newList: List<Memory>) {
        memories = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_polaroid, parent, false)
        return MemoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        holder.bind(memories[position])
    }

    override fun getItemCount(): Int = memories.size

    inner class MemoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_polaroid_image)
        private val tvCaption: TextView = itemView.findViewById(R.id.tv_polaroid_caption)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_polaroid_date)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_memory)

        fun bind(memory: Memory) {
            tvCaption.text = memory.title
            tvDate.text = memory.dateStr
            if (memory.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(memory.imageUrl)
                    .centerCrop()
                    .into(ivImage)
            } else {
                ivImage.setImageResource(0)
            }
            
            itemView.setOnClickListener {
                onItemClick(memory)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(memory)
            }
        }
    }
}
