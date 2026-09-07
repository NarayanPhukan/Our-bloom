package com.ourbloom.app.chat.stickers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.ourbloom.app.R

class StickerAdapter(
    private val onStickerClick: (StickerManager.Sticker) -> Unit,
    private val onStickerLongClick: (StickerManager.Sticker, View) -> Unit
) : ListAdapter<StickerManager.Sticker, StickerAdapter.StickerViewHolder>(StickerDiffCallback()) {

    class StickerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumb: ImageView = itemView.findViewById(R.id.iv_sticker_thumb)
        val ivStar: ImageView = itemView.findViewById(R.id.iv_sticker_star)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker_grid, parent, false)
        return StickerViewHolder(view)
    }

    override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
        val sticker = getItem(position)

        Glide.with(holder.ivThumb.context)
            .load(sticker.file)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .fitCenter()
            .into(holder.ivThumb)

        holder.ivStar.visibility = if (sticker.isFavorite) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            onStickerClick(sticker)
        }

        holder.itemView.setOnLongClickListener { view ->
            onStickerLongClick(sticker, view)
            true
        }
    }

    class StickerDiffCallback : DiffUtil.ItemCallback<StickerManager.Sticker>() {
        override fun areItemsTheSame(oldItem: StickerManager.Sticker, newItem: StickerManager.Sticker): Boolean {
            return oldItem.file.absolutePath == newItem.file.absolutePath
        }

        override fun areContentsTheSame(oldItem: StickerManager.Sticker, newItem: StickerManager.Sticker): Boolean {
            return oldItem.isFavorite == newItem.isFavorite && oldItem.isRecent == newItem.isRecent
        }
    }
}
