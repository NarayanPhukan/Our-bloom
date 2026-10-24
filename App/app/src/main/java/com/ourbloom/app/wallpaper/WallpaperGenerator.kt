package com.ourbloom.app.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.ourbloom.app.data.models.CoupleWallpaperConfig
import kotlin.math.max

object WallpaperGenerator {

    fun generateWallpaperBitmap(
        sourceBitmap: Bitmap?,
        config: CoupleWallpaperConfig,
        daysTogether: Long,
        coupleNames: String,
        anniversaryDateStr: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val width = if (targetWidth > 0) targetWidth else 1080
        val height = if (targetHeight > 0) targetHeight else 2400

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. Draw Background (Source Photo or Preset Gradient)
        if (sourceBitmap != null && !sourceBitmap.isRecycled) {
            drawCenterCropBitmap(canvas, sourceBitmap, width, height)
        } else {
            drawPresetGradient(canvas, config.presetTheme, width, height)
        }

        // 2. Draw Soft Scrims (Top and Bottom Vignettes)
        val dimAlpha = (config.dimLevel.coerceIn(0.1f, 0.85f) * 255).toInt()
        val bottomScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, height * 0.45f,
                0f, height.toFloat(),
                Color.TRANSPARENT,
                Color.argb(dimAlpha, 15, 15, 25),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, height * 0.45f, width.toFloat(), height.toFloat(), bottomScrimPaint)

        // Subtle top scrim for status bar readability
        val topScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f,
                0f, height * 0.20f,
                Color.argb((dimAlpha * 0.6f).toInt(), 10, 10, 15),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.20f, topScrimPaint)

        // 3. Draw Romantic Couple Overlay Elements
        val badgeY = when (config.badgePosition) {
            "TOP" -> height * 0.28f
            "CENTER" -> height * 0.50f
            else -> height * 0.72f // BOTTOM (recommended for lock/home screen icons)
        }

        drawCoupleCardOverlay(
            canvas = canvas,
            width = width,
            centerY = badgeY,
            days = daysTogether,
            names = if (config.showCoupleNames) coupleNames else "",
            dateStr = anniversaryDateStr,
            quote = if (config.showMilestone) config.quoteText else "",
            showDays = config.showDaysCounter
        )

        return output
    }

    private fun drawCenterCropBitmap(canvas: Canvas, source: Bitmap, targetW: Int, targetH: Int) {
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()
        val scale = max(targetW / srcW, targetH / srcH)

        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val left = (targetW - scaledW) / 2f
        val top = (targetH - scaledH) / 2f

        val dstRect = RectF(left, top, left + scaledW, top + scaledH)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(source, null, dstRect, paint)
    }

    private fun drawPresetGradient(canvas: Canvas, theme: String, w: Int, h: Int) {
        val colors = when (theme) {
            "ROSE_SUNSET" -> intArrayOf(Color.parseColor("#FF758C"), Color.parseColor("#FF7EB3"), Color.parseColor("#79184B"))
            "STARRY_LOVE" -> intArrayOf(Color.parseColor("#0F2027"), Color.parseColor("#203A43"), Color.parseColor("#2C5364"))
            "CHERRY_BLOSSOM" -> intArrayOf(Color.parseColor("#F8BBD0"), Color.parseColor("#FF80AB"), Color.parseColor("#C2185B"))
            "GOLDEN_HOUR" -> intArrayOf(Color.parseColor("#F3904F"), Color.parseColor("#C0392B"), Color.parseColor("#3B4371"))
            else -> intArrayOf(Color.parseColor("#E85D75"), Color.parseColor("#880E4F"), Color.parseColor("#1A091A"))
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors, null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private fun drawCoupleCardOverlay(
        canvas: Canvas,
        width: Int,
        centerY: Float,
        days: Long,
        names: String,
        dateStr: String,
        quote: String,
        showDays: Boolean
    ) {
        val cardWidth = width * 0.88f
        val cardHeight = 320f
        val cardLeft = (width - cardWidth) / 2f
        val cardTop = centerY - cardHeight / 2f
        val cardRight = cardLeft + cardWidth
        val cardBottom = cardTop + cardHeight
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

        // Frosted Card Background with soft blush glow
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 25, 20, 30) // Frosted glass dark tint
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 255, 205, 210) // Soft rose gold outline
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, cardBgPaint)
        canvas.drawRoundRect(cardRect, 48f, 48f, cardBorderPaint)

        // Days Badge
        var currentY = cardTop + 72f
        if (showDays) {
            val daysText = "✦  $days DAYS TOGETHER  ✦"
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF80AB") // Glowing Rose Pink
                textAlign = Paint.Align.CENTER
                textSize = 38f
                isFakeBoldText = true
                letterSpacing = 0.08f
                setShadowLayer(8f, 0f, 2f, Color.argb(180, 233, 30, 99))
            }
            canvas.drawText(daysText, width / 2f, currentY, badgePaint)
            currentY += 68f
        }

        // Couple Names
        if (names.isNotBlank()) {
            val namesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = 52f
                isFakeBoldText = true
                setShadowLayer(6f, 0f, 3f, Color.BLACK)
            }
            canvas.drawText(names, width / 2f, currentY, namesPaint)
            currentY += 56f
        }

        // Anniversary / Since date
        if (dateStr.isNotBlank()) {
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E0E0E0")
                textAlign = Paint.Align.CENTER
                textSize = 30f
                letterSpacing = 0.04f
                setShadowLayer(4f, 0f, 2f, Color.BLACK)
            }
            canvas.drawText(dateStr, width / 2f, currentY, datePaint)
            currentY += 46f
        }

        // Quote / Milestone
        if (quote.isNotBlank()) {
            val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFCDD2")
                textAlign = Paint.Align.CENTER
                textSize = 28f
                setShadowLayer(4f, 0f, 2f, Color.BLACK)
            }
            canvas.drawText(quote, width / 2f, currentY, quotePaint)
        }
    }
}
