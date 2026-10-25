package com.ourbloom.app.wallpaper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.ourbloom.app.R

class WallpaperTemplateAdapter(
    private val templates: List<WallpaperTemplate>,
    private var selectedTemplateId: String,
    private val onTemplateSelected: (WallpaperTemplate) -> Unit
) : RecyclerView.Adapter<WallpaperTemplateAdapter.TemplateViewHolder>() {

    fun updateSelected(newId: String) {
        selectedTemplateId = newId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wallpaper_template_card, parent, false)
        return TemplateViewHolder(view)
    }

    override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) {
        holder.bind(templates[position])
    }

    override fun getItemCount(): Int = templates.size

    inner class TemplateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardRoot: View = itemView.findViewById(R.id.card_template_root)
        private val cardFrame: MaterialCardView = itemView.findViewById(R.id.card_template_frame)
        private val ivPreview: ImageView = itemView.findViewById(R.id.iv_template_preview)
        private val badgeSelected: View = itemView.findViewById(R.id.badge_selected_indicator)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_template_title)
        private val tvTag: TextView = itemView.findViewById(R.id.tv_template_tag)
        private val tvDesc: TextView = itemView.findViewById(R.id.tv_template_desc)
        private val btnSelect: MaterialButton = itemView.findViewById(R.id.btn_select_template)

        fun bind(template: WallpaperTemplate) {
            tvTitle.text = template.title
            tvTag.text = template.badge
            tvDesc.text = template.description
            ivPreview.setImageResource(template.previewRes)

            val isSelected = (template.id == selectedTemplateId)
            badgeSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

            if (isSelected) {
                cardFrame.strokeColor = ContextCompat.getColor(itemView.context, R.color.botanical_rose)
                cardFrame.strokeWidth = (itemView.resources.displayMetrics.density * 2.5f).toInt()
                btnSelect.text = "Active ✨"
                btnSelect.isEnabled = false
                btnSelect.alpha = 0.8f
            } else {
                cardFrame.strokeColor = ContextCompat.getColor(itemView.context, R.color.sheet_card_stroke)
                cardFrame.strokeWidth = (itemView.resources.displayMetrics.density * 1.5f).toInt()
                btnSelect.text = "Select ✨"
                btnSelect.isEnabled = true
                btnSelect.alpha = 1.0f
            }

            val selectAction = View.OnClickListener {
                onTemplateSelected(template)
            }
            cardRoot.setOnClickListener(selectAction)
            btnSelect.setOnClickListener(selectAction)
        }
    }
}
