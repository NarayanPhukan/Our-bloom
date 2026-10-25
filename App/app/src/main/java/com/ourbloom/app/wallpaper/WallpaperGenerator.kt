package com.ourbloom.app.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.ourbloom.app.R
import com.ourbloom.app.data.models.CoupleWallpaperConfig
import kotlin.math.max

object WallpaperGenerator {

    data class CollageSlot(
        val relX: Float,
        val relY: Float,
        val relW: Float,
        val relH: Float
    )

    val HEART_COLLAGE_SLOTS = listOf(
        // Centerpiece (Most prominent)
        CollageSlot(0.2882f, 0.2871f, 0.4410f, 0.2881f), // 0: Centerpiece
        // Left & Right Upper Wings
        CollageSlot(0.0035f, 0.2451f, 0.2778f, 0.1289f), // 1: Big Left Wing
        CollageSlot(0.7326f, 0.2402f, 0.2674f, 0.1309f), // 2: Big Right Wing
        // Top Lobes
        CollageSlot(0.1458f, 0.1826f, 0.1372f, 0.0576f), // 3: Top Left Lobe
        CollageSlot(0.7309f, 0.1787f, 0.1372f, 0.0576f), // 4: Top Right Lobe
        // Top Inner Cleft
        CollageSlot(0.2934f, 0.2217f, 0.1562f, 0.0586f), // 5: Top Inner Left
        CollageSlot(0.5625f, 0.2197f, 0.1580f, 0.0596f), // 6: Top Inner Right
        // Mid Wings
        CollageSlot(0.1024f, 0.3789f, 0.1753f, 0.0996f), // 7: Mid Left Box
        CollageSlot(0.7361f, 0.3760f, 0.1823f, 0.1006f), // 8: Mid Right Box
        // Lower Taper
        CollageSlot(0.1719f, 0.4844f, 0.1076f, 0.0605f), // 9: Lower Left
        CollageSlot(0.7396f, 0.4824f, 0.1094f, 0.0615f), // 10: Lower Right
        // Bottom Point
        CollageSlot(0.4306f, 0.5811f, 0.1597f, 0.0771f), // 11: Bottom Center
        CollageSlot(0.3385f, 0.5811f, 0.0816f, 0.0459f), // 12: Bottom Point Left
        CollageSlot(0.6007f, 0.5811f, 0.0816f, 0.0459f), // 13: Bottom Point Right
        // Outer Accents
        CollageSlot(0.0625f, 0.2041f, 0.0747f, 0.0371f), // 14: Top Outer Left Tab
        CollageSlot(0.8802f, 0.1982f, 0.0764f, 0.0381f), // 15: Top Outer Right Tab
        CollageSlot(0.0156f, 0.3799f, 0.0781f, 0.0439f), // 16: Mid Outer Left Tab
        CollageSlot(0.9253f, 0.3760f, 0.0747f, 0.0439f)  // 17: Mid Outer Right Tab
    )

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

    /**
     * Generates a romantic Heart Memories Collage wallpaper from multiple gallery photos,
     * framed with the vintage watercolor bows and ribbons.
     */
    fun generateHeartCollageBitmap(
        context: Context,
        photos: List<Bitmap>,
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

        // 1. Draw base soft romantic background
        canvas.drawColor(Color.parseColor("#F5F5F7"))

        // 2. Draw the 18 photo slots
        val fallbackColors = intArrayOf(
            Color.parseColor("#FF5E7E"), Color.parseColor("#FF8FAB"),
            Color.parseColor("#FFB3C6"), Color.parseColor("#C9184A"),
            Color.parseColor("#A4133C"), Color.parseColor("#FF69B4"),
            Color.parseColor("#FFC0CB"), Color.parseColor("#FF1493")
        )

        for (i in HEART_COLLAGE_SLOTS.indices) {
            val slot = HEART_COLLAGE_SLOTS[i]
            val left = (slot.relX * width) - 2f
            val top = (slot.relY * height) - 2f
            val right = (slot.relX + slot.relW) * width + 2f
            val bottom = (slot.relY + slot.relH) * height + 2f
            val rect = RectF(left, top, right, bottom)

            if (photos.isNotEmpty()) {
                val photo = photos[i % photos.size]
                drawCenterCropToRect(canvas, photo, rect)
            } else {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fallbackColors[i % fallbackColors.size]
                }
                canvas.drawRect(rect, p)
            }
        }

        // 3. Draw the watercolor bows and frame overlay on top
        try {
            val frameBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.template_heart_ribbon_frame)
            if (frameBitmap != null) {
                val dstRect = Rect(0, 0, width, height)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(frameBitmap, null, dstRect, paint)
            }
        } catch (e: Exception) {
            android.util.Log.e("WallpaperGenerator", "Error loading template_heart_ribbon_frame", e)
        }

        // 4. Subtle bottom scrim for dock and widget readability
        val dimAlpha = (config.dimLevel.coerceIn(0.05f, 0.6f) * 255).toInt()
        val bottomScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, height * 0.70f,
                0f, height.toFloat(),
                Color.TRANSPARENT,
                Color.argb(dimAlpha, 15, 15, 25),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, height * 0.70f, width.toFloat(), height.toFloat(), bottomScrimPaint)

        // 5. Draw Romantic Couple Overlay Card below the heart collage at y = 0.76f
        val badgeY = height * 0.76f
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

    private fun drawCenterCropToRect(canvas: Canvas, source: Bitmap, dstRect: RectF) {
        if (source.isRecycled) return
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()
        val dstW = dstRect.width()
        val dstH = dstRect.height()

        val scale = max(dstW / srcW, dstH / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val left = dstRect.centerX() - scaledW / 2f
        val top = dstRect.centerY() - scaledH / 2f

        canvas.save()
        canvas.clipRect(dstRect)
        val drawRect = RectF(left, top, left + scaledW, top + scaledH)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(source, null, drawRect, paint)
        canvas.restore()
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

        // Frosted Glass Background
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(135, 20, 20, 30) // Translucent dark glass
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

        // Subtle Rose Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(160, 255, 182, 193) // Light pink glow
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, borderPaint)

        var currentY = cardTop + 72f

        // Days Counter
        if (showDays) {
            val daysNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFFFFF")
                textAlign = Paint.Align.CENTER
                textSize = 62f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                setShadowLayer(8f, 0f, 4f, Color.argb(160, 0, 0, 0))
            }
            canvas.drawText("$days Days Together 💕", width / 2f, currentY, daysNumberPaint)
            currentY += 68f
        }

        // Couple Names
        if (names.isNotBlank()) {
            val namesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF80AB")
                textAlign = Paint.Align.CENTER
                textSize = 40f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                setShadowLayer(6f, 0f, 3f, Color.argb(160, 0, 0, 0))
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
