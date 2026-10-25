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

    // High-precision normalized coordinates for 18-photo Heart Collage
    val HEART_COLLAGE_SLOTS = listOf(
        CollageSlot(0.2907f, 0.2967f, 0.4639f, 0.2971f), // 0: Centerpiece (Most prominent)
        CollageSlot(0.0000f, 0.2533f, 0.2972f, 0.1379f), // 1: Big Left Wing
        CollageSlot(0.7546f, 0.2483f, 0.2454f, 0.1392f), // 2: Big Right Wing
        CollageSlot(0.7528f, 0.3867f, 0.1991f, 0.1079f), // 3: Mid Right Box
        CollageSlot(0.0981f, 0.3900f, 0.1944f, 0.1067f), // 4: Mid Left Box
        CollageSlot(0.4315f, 0.5925f, 0.1769f, 0.0858f), // 5: Bottom Center
        CollageSlot(0.5741f, 0.2275f, 0.1824f, 0.0671f), // 6: Top Inner Right
        CollageSlot(0.2981f, 0.2292f, 0.1833f, 0.0667f), // 7: Top Inner Left
        CollageSlot(0.7556f, 0.1837f, 0.1574f, 0.0667f), // 8: Top Right Lobe
        CollageSlot(0.1463f, 0.1888f, 0.1546f, 0.0658f), // 9: Top Left Lobe
        CollageSlot(0.7528f, 0.4938f, 0.1250f, 0.0692f), // 10: Lower Right
        CollageSlot(0.1676f, 0.4954f, 0.1241f, 0.0692f), // 11: Lower Left
        CollageSlot(0.3361f, 0.5925f, 0.0981f, 0.0537f), // 12: Bottom Left Point
        CollageSlot(0.6065f, 0.5925f, 0.0963f, 0.0537f), // 13: Bottom Right Point
        CollageSlot(0.0102f, 0.3904f, 0.0917f, 0.0508f), // 14: Mid Outer Left Tab
        CollageSlot(0.9102f, 0.2046f, 0.0898f, 0.0450f), // 15: Top Outer Right Tab
        CollageSlot(0.0583f, 0.2108f, 0.0898f, 0.0446f), // 16: Top Outer Left Tab
        CollageSlot(0.9491f, 0.3862f, 0.0509f, 0.0517f)  // 17: Mid Outer Right Tab
    )

    fun generateWallpaperBitmap(
        sourceBitmap: Bitmap?,
        config: CoupleWallpaperConfig,
        daysTogether: Long,
        coupleNames: String,
        anniversaryDateStr: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap = generateWallpaperBitmap(
        null, sourceBitmap, config, daysTogether, coupleNames, anniversaryDateStr, targetWidth, targetHeight
    )

    fun generateWallpaperBitmap(
        context: Context?,
        sourceBitmap: Bitmap?,
        config: CoupleWallpaperConfig,
        daysTogether: Long,
        coupleNames: String,
        anniversaryDateStr: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        if (config.templateId == "VINTAGE_SCRAPBOOK" && context != null) {
            return generateVintageScrapbookBitmap(
                context = context,
                sourceBitmap = sourceBitmap,
                config = config,
                daysTogether = daysTogether,
                coupleNames = coupleNames,
                anniversaryDateStr = anniversaryDateStr,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )
        }

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

        val isLight = config.presetTheme == "CHERRY_BLOSSOM"
        drawCoupleCardOverlay(
            canvas = canvas,
            width = width,
            centerY = badgeY,
            days = daysTogether,
            names = if (config.showCoupleNames) coupleNames else "",
            dateStr = anniversaryDateStr,
            quote = if (config.showMilestone) config.quoteText else "",
            showDays = config.showDaysCounter,
            isLightBackground = isLight
        )

        return output
    }

    /**
     * Generates a romantic Heart Memories Collage wallpaper from multiple gallery photos,
     * framed with the vintage watercolor bows and ribbons with ultra-HD vector anti-aliased frames.
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
        canvas.drawColor(Color.parseColor("#F8F6F4"))

        // 2. Draw HD Watercolor Ribbon Background (1080x2400 unwarped native asset)
        try {
            val bgBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.template_heart_ribbon_bg)
            if (bgBitmap != null) {
                val dstRect = Rect(0, 0, width, height)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(bgBitmap, null, dstRect, paint)
            }
        } catch (e: Exception) {
            android.util.Log.e("WallpaperGenerator", "Error loading template_heart_ribbon_bg", e)
        }

        // 3. Setup Vector Paints for razor-sharp photo frame rendering
        val density = width / 1080f
        val strokeW = 3.8f * density

        // Crisp vector frame border (graphite charcoal)
        val frameBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            color = Color.parseColor("#3C3C3E")
            strokeJoin = Paint.Join.ROUND
        }

        // Subtle inner highlight stroke for photo depth bevel
        val innerHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            color = Color.argb(55, 255, 255, 255)
        }

        val fallbackColors = intArrayOf(
            Color.parseColor("#FF5E7E"), Color.parseColor("#FF8FAB"),
            Color.parseColor("#FFB3C6"), Color.parseColor("#C9184A"),
            Color.parseColor("#A4133C"), Color.parseColor("#FF69B4"),
            Color.parseColor("#FFC0CB"), Color.parseColor("#FF1493")
        )

        // 4. Draw the 18 photo slots with vector frames
        for (i in HEART_COLLAGE_SLOTS.indices) {
            val slot = HEART_COLLAGE_SLOTS[i]
            val left = slot.relX * width
            val top = slot.relY * height
            val right = (slot.relX + slot.relW) * width
            val bottom = (slot.relY + slot.relH) * height
            val rect = RectF(left, top, right, bottom)

            // Draw photo content
            if (photos.isNotEmpty()) {
                val photo = photos[i % photos.size]
                drawCenterCropToRect(canvas, photo, rect)
            } else {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fallbackColors[i % fallbackColors.size]
                }
                canvas.drawRect(rect, p)
            }

            // Draw razor-sharp vector frame border
            canvas.drawRect(rect, frameBorderPaint)

            // Draw subtle inner highlight
            val innerRect = RectF(
                left + 1.2f * density,
                top + 1.2f * density,
                right - 1.2f * density,
                bottom - 1.2f * density
            )
            canvas.drawRect(innerRect, innerHighlightPaint)
        }

        // 5. Subtle bottom scrim for lock screen / launcher widget and dock readability
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

        // 6. Draw Minimalist & Elegant Romantic Couple Overlay Card below the heart collage at y = 0.765f
        val badgeY = height * 0.765f
        drawCoupleCardOverlay(
            canvas = canvas,
            width = width,
            centerY = badgeY,
            days = daysTogether,
            names = if (config.showCoupleNames) coupleNames else "",
            dateStr = anniversaryDateStr,
            quote = if (config.showMilestone) config.quoteText else "",
            showDays = config.showDaysCounter,
            isLightBackground = true
        )

        return output
    }

    /**
     * Generates a romantic Vintage Scrapbook wallpaper:
     * Handmade paper background, vintage newspaper scrap & black leaves,
     * central couple photo framed with pink kiss print & thumbprint heart,
     * and a botanical charcoal flower blossom with custom typewriter caption.
     */
    fun generateVintageScrapbookBitmap(
        context: Context,
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

        // 1. Soft natural paper tone
        canvas.drawColor(Color.parseColor("#F4F0E8"))

        // 2. Central rectangular photo slot
        // Normalized coordinates from 576x1024:
        // x0=107/576, y0=381/1024, x1=455/576, y1=642/1024
        val relX = 107f / 576f
        val relY = 381f / 1024f
        val relW = 348f / 576f
        val relH = 261f / 1024f

        val photoRect = RectF(relX * width, relY * height, (relX + relW) * width, (relY + relH) * height)

        val photoToDraw = if (sourceBitmap != null && !sourceBitmap.isRecycled) {
            sourceBitmap
        } else {
            BitmapFactory.decodeResource(context.resources, R.drawable.sample_couple_portrait)
        }

        if (photoToDraw != null && !photoToDraw.isRecycled) {
            drawCenterCropToRect(canvas, photoToDraw, photoRect, applyMonochrome = true)
        }

        // 3. Draw seamless scrapbook frame (torn newspaper, black leaves, pink kiss mark, pink thumbprint heart, charcoal blossom)
        try {
            val frameBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.template_vintage_scrapbook_frame)
            if (frameBitmap != null) {
                val framePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(frameBitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), framePaint)
            }
        } catch (e: Exception) {
            // Frame rendering fallback
        }

        val density = width / 1080f

        // 4. Draw Romantic Caption below photo
        val caption = if (config.quoteText.isNotBlank()) config.quoteText else "eyes speak louder than words."
        val captionY = (668f / 1024f) * height
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#383431")
            textAlign = Paint.Align.CENTER
            textSize = 21f * density
            letterSpacing = 0.04f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
        }
        val maxTextWidth = width * 0.75f
        val measuredW = captionPaint.measureText(caption)
        if (measuredW > maxTextWidth) {
            captionPaint.textSize = (21f * density) * (maxTextWidth / measuredW)
        }
        canvas.drawText(caption, width / 2f, captionY, captionPaint)

        // 5. Draw Couple Nicknames / Days Together below caption (if enabled)
        if (config.showCoupleNames && coupleNames.isNotBlank()) {
            val namesY = captionY + 36f * density
            val namesText = if (config.showDaysCounter && daysTogether > 0) {
                "$coupleNames  •  $daysTogether Days"
            } else {
                coupleNames
            }
            val namesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#7D6F77")
                textAlign = Paint.Align.CENTER
                textSize = 17f * density
                letterSpacing = 0.03f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            }
            val measuredNamesW = namesPaint.measureText(namesText)
            if (measuredNamesW > maxTextWidth) {
                namesPaint.textSize = (17f * density) * (maxTextWidth / measuredNamesW)
            }
            canvas.drawText(namesText, width / 2f, namesY, namesPaint)
        }

        return output
    }

    private fun drawCenterCropToRect(
        canvas: Canvas,
        source: Bitmap,
        dstRect: RectF,
        applyMonochrome: Boolean = false
    ) {
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
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            if (applyMonochrome) {
                val cm = android.graphics.ColorMatrix().apply { setSaturation(0f) }
                colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            }
        }
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

    /**
     * Draws a minimalistic, elegant, and clean floating frosted pill badge on the wallpaper.
     * Adapts gracefully to light/paper themes (pearl glass) and dark themes (obsidian glass).
     */
    private fun drawCoupleCardOverlay(
        canvas: Canvas,
        width: Int,
        centerY: Float,
        days: Long,
        names: String,
        dateStr: String,
        quote: String,
        showDays: Boolean,
        isLightBackground: Boolean = false
    ) {
        if (!showDays && names.isBlank() && dateStr.isBlank() && quote.isBlank()) {
            return
        }

        val density = width / 1080f
        val cardWidth = width * 0.76f
        val hasQuote = quote.isNotBlank()
        val cardHeight = if (hasQuote) 190f * density else 156f * density
        val cardLeft = (width - cardWidth) / 2f
        val cardTop = centerY - cardHeight / 2f
        val cardRight = cardLeft + cardWidth
        val cardBottom = cardTop + cardHeight
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
        val pillRadius = cardHeight / 2f

        // 1. Subtle Outer Ambient Shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(20, 0, 0, 0)
        }
        val shadowRect = RectF(
            cardLeft + 2f * density,
            cardTop + 3f * density,
            cardRight - 2f * density,
            cardBottom + 5f * density
        )
        canvas.drawRoundRect(shadowRect, pillRadius, pillRadius, shadowPaint)

        // 2. Translucent Glass Capsule Fill
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLightBackground) {
                Color.argb(225, 255, 255, 255) // Translucent pearl glass
            } else {
                Color.argb(175, 25, 25, 32) // Translucent obsidian glass
            }
        }
        canvas.drawRoundRect(cardRect, pillRadius, pillRadius, cardPaint)

        // 3. Delicate Hairline Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * density
            color = if (isLightBackground) {
                Color.argb(190, 215, 195, 200) // Soft rose-quartz hairline
            } else {
                Color.argb(80, 255, 255, 255) // Subtle translucent white
            }
        }
        canvas.drawRoundRect(cardRect, pillRadius, pillRadius, borderPaint)

        // 4. Refined Minimalist Typography
        val titleColor = if (isLightBackground) Color.parseColor("#2C2C2E") else Color.parseColor("#FFFFFF")
        val subtitleColor = if (isLightBackground) Color.parseColor("#825A66") else Color.parseColor("#FF80AB")
        val dateColor = if (isLightBackground) Color.parseColor("#8E8E93") else Color.parseColor("#E0E0E0")

        val hasSubtitle = names.isNotBlank() || dateStr.isNotBlank()
        var currentY = when {
            showDays && hasSubtitle -> cardTop + 58f * density
            showDays || hasSubtitle -> cardTop + (cardHeight * 0.58f)
            else -> cardTop + (cardHeight * 0.50f)
        }

        val maxTextWidth = cardWidth - 36f * density

        // Days Counter: Clean, tracked uppercase
        if (showDays) {
            val daysPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = titleColor
                textAlign = Paint.Align.CENTER
                textSize = 34f * density
                letterSpacing = 0.08f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                if (!isLightBackground) {
                    setShadowLayer(4f, 0f, 2f, Color.argb(120, 0, 0, 0))
                }
            }
            val daysText = "$days DAYS TOGETHER"
            val measuredW = daysPaint.measureText(daysText)
            if (measuredW > maxTextWidth) {
                daysPaint.textSize = (34f * density) * (maxTextWidth / measuredW)
            }
            canvas.drawText(daysText, width / 2f, currentY, daysPaint)
            currentY += 46f * density
        }

        // Subtitle: Couple Names & Anniversary/Date combined gracefully
        val cleanDate = dateStr.replace("Together since ", "Since ")
        val subtitleText = when {
            names.isNotBlank() && cleanDate.isNotBlank() -> "$names  •  $cleanDate"
            names.isNotBlank() -> names
            cleanDate.isNotBlank() -> cleanDate
            else -> ""
        }

        if (subtitleText.isNotBlank()) {
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subtitleColor
                textAlign = Paint.Align.CENTER
                textSize = 23f * density
                letterSpacing = 0.02f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            }
            val measuredW = subPaint.measureText(subtitleText)
            if (measuredW > maxTextWidth) {
                subPaint.textSize = (23f * density) * (maxTextWidth / measuredW)
            }
            canvas.drawText(subtitleText, width / 2f, currentY, subPaint)
            currentY += 38f * density
        }

        // Optional Quote / Milestone
        if (quote.isNotBlank()) {
            val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = dateColor
                textAlign = Paint.Align.CENTER
                textSize = 20f * density
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
            }
            val quoteText = "“$quote”"
            val measuredW = quotePaint.measureText(quoteText)
            if (measuredW > maxTextWidth) {
                quotePaint.textSize = (20f * density) * (maxTextWidth / measuredW)
            }
            canvas.drawText(quoteText, width / 2f, currentY, quotePaint)
        }
    }
}
