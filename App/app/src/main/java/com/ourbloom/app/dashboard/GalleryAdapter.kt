package com.ourbloom.app.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ourbloom.app.R
import com.ourbloom.app.data.models.Memory

class GalleryAdapter(private val onItemClick: (Memory) -> Unit) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    private var memories: List<Memory> = emptyList()

    fun submitList(newList: List<Memory>) {
        memories = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_polaroid, parent, false)
        return GalleryViewHolder(view)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        holder.bind(memories[position])
    }

    override fun getItemCount(): Int = memories.size

    inner class GalleryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_polaroid_image)
        private val tvCaption: TextView = itemView.findViewById(R.id.tv_polaroid_caption)

        fun bind(memory: Memory) {
            tvCaption.text = memory.title
            if (memory.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(memory.imageUrl)
                    .placeholder(R.drawable.placeholder_memory)
                    .error(R.drawable.placeholder_memory)
                    .centerCrop()
                    .into(ivImage)
            } else {
                ivImage.setImageResource(R.drawable.placeholder_memory)
            }
            
            itemView.setOnClickListener {
                onItemClick(memory)
            }
        }
    }
}
