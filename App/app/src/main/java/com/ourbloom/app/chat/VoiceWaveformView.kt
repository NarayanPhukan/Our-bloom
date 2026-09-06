package com.ourbloom.app.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

class VoiceWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barRect = RectF()

    var progress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var playedColor: Int = 0xFFFFFFFF.toInt()
        set(value) {
            field = value
            playedPaint.color = value
            invalidate()
        }

    var unplayedColor: Int = 0x4DFFFFFF.toInt()
        set(value) {
            field = value
            unplayedPaint.color = value
            invalidate()
        }

    var onSeekListener: ((Float) -> Unit)? = null

    // Natural speech pattern baseline
    private var waveformBars: FloatArray = floatArrayOf(
        0.30f, 0.45f, 0.70f, 0.90f, 0.60f, 0.40f, 0.75f, 1.00f, 0.85f, 0.55f,
        0.35f, 0.65f, 0.95f, 0.80f, 0.50f, 0.70f, 0.90f, 0.60f, 0.40f, 0.85f,
        0.95f, 0.75f, 0.45f, 0.60f, 0.80f, 0.65f, 0.40f, 0.30f, 0.50f, 0.35f
    )

    init {
        playedPaint.color = playedColor
        unplayedPaint.color = unplayedColor
    }

    fun setWaveformSeed(seed: String) {
        if (seed.isBlank()) return
        val count = 30
        val bars = FloatArray(count)
        val hash = seed.hashCode().toLong()
        for (i in 0 until count) {
            val base = sin((i + 1) * 0.45 + (hash and 0xFF) * 0.05).toFloat()
            val variance = (((hash shr (i % 16)) and 0x1F) / 31f) * 0.45f
            bars[i] = (0.22f + 0.48f * abs(base) + variance).coerceIn(0.18f, 1.0f)
        }
        waveformBars = bars
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val newProgress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                progress = newProgress
                onSeekListener?.invoke(newProgress)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val newProgress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                progress = newProgress
                onSeekListener?.invoke(newProgress)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val newProgress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                progress = newProgress
                onSeekListener?.invoke(newProgress)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val count = waveformBars.size
        val barWidth = 2.6f * resources.displayMetrics.density
        val totalBarWidth = count * barWidth
        val availableSpacing = if (count > 1) (w - totalBarWidth) / (count - 1) else 2f
        val spacing = availableSpacing.coerceAtLeast(1.4f * resources.displayMetrics.density)
        val cornerRadius = barWidth / 2f
        val centerY = h / 2f

        var currentX = 0f
        val currentProgressX = progress * w

        for (i in 0 until count) {
            val barHeight = (waveformBars[i] * (h - 4f)).coerceAtLeast(barWidth)
            val top = centerY - (barHeight / 2f)
            val bottom = centerY + (barHeight / 2f)
            val right = currentX + barWidth

            barRect.set(currentX, top, right, bottom)

            val paint = if (currentX <= currentProgressX) playedPaint else unplayedPaint
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, paint)

            currentX += barWidth + spacing
            if (currentX > w) break
        }
    }
}
