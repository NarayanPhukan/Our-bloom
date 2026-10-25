package com.ourbloom.app.wallpaper

import androidx.annotation.DrawableRes
import com.ourbloom.app.R

data class WallpaperTemplate(
    val id: String,
    val title: String,
    val badge: String,
    val description: String,
    @get:DrawableRes val previewRes: Int
)

object WallpaperTemplateRegistry {
    val TEMPLATES = listOf(
        WallpaperTemplate(
            id = "VINTAGE_SCRAPBOOK",
            title = "Vintage Scrapbook 💋",
            badge = "Kiss & Thumbprint Heart",
            description = "Handmade paper, newspaper scrap, botanical blossom & kiss print",
            previewRes = R.drawable.preview_template_vintage_scrapbook
        ),
        WallpaperTemplate(
            id = "HEART_MEMORIES",
            title = "Heart Memories 💖",
            badge = "18 Photo Slots • Bows",
            description = "Vintage watercolor ribbons framing your love memories",
            previewRes = R.drawable.preview_template_heart_collage
        ),
        WallpaperTemplate(
            id = "CLASSIC",
            title = "Classic Photo 📷",
            badge = "Custom Portrait",
            description = "Full-screen couple photo with intimate name badge",
            previewRes = R.drawable.preview_template_classic_photo
        ),
        WallpaperTemplate(
            id = "THEMES",
            title = "Color Themes 🎨",
            badge = "Romantic Sunset & Stars",
            description = "Atmospheric dual-tone gradients with soft vignettes",
            previewRes = R.drawable.preview_template_romantic_theme
        )
    )
}
