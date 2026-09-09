package com.ourbloom.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import kotlin.concurrent.thread

class ScratchCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var scratchBitmap: Bitmap? = null
    private var scratchCanvas: Canvas? = null
    private val scratchPath = Path()

    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 60f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5C3A43")
        textSize = 38f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A545E")
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private var lastX = 0f
    private var lastY = 0f
    private var lastHapticTime = 0L
    private var isRevealed = false
    private var isCheckingProgress = false

    var onScratchRevealed: (() -> Unit)? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && !isRevealed) {
            initScratchFoil(w, h)
        }
    }

    private fun initScratchFoil(w: Int, h: Int) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cvs = Canvas(bmp)

        // Rose-gold foil shimmer gradient
        val gradient = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(
                Color.parseColor("#FAD2E1"),
                Color.parseColor("#FFD6A5"),
                Color.parseColor("#FAD2E1"),
                Color.parseColor("#FFF1E6")
            ),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        val foilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
        }

        // Draw foil background with smooth rounded corners
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        cvs.drawRoundRect(rect, 24f, 24f, foilPaint)

        // Foil subtle border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#40FFFFFF")
        }
        cvs.drawRoundRect(rect, 24f, 24f, borderPaint)

        // Center Instruction Text
        val centerY = h / 2f
        cvs.drawText("✨ Secret Love Note 🎁", w / 2f, centerY - 14f, textPaint)
        cvs.drawText("Scratch to reveal my darling...", w / 2f, centerY + 32f, subTextPaint)

        scratchBitmap?.recycle()
        scratchBitmap = bmp
        scratchCanvas = cvs
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        scratchBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                canvas.drawBitmap(bmp, 0f, 0f, null)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isRevealed || scratchCanvas == null) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                scratchPath.reset()
                scratchPath.moveTo(x, y)
                lastX = x
                lastY = y
                eraseAt(x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = kotlin.math.abs(x - lastX)
                val dy = kotlin.math.abs(y - lastY)
                if (dx >= 4 || dy >= 4) {
                    scratchPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                    lastX = x
                    lastY = y
                    scratchCanvas?.drawPath(scratchPath, erasePaint)
                    invalidate()
                    triggerScratchHaptic()
                    checkScratchProgressAsync()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                scratchPath.lineTo(x, y)
                scratchCanvas?.drawPath(scratchPath, erasePaint)
                scratchPath.reset()
                invalidate()
                checkScratchProgressAsync(forceRevealCheck = true)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun eraseAt(x: Float, y: Float) {
        scratchCanvas?.drawCircle(x, y, 30f, erasePaint)
        invalidate()
        triggerScratchHaptic()
    }

    private fun triggerScratchHaptic() {
        val now = System.currentTimeMillis()
        if (now - lastHapticTime > 75) {
            lastHapticTime = now
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(12)
                }
            } catch (_: Exception) {}
        }
    }

    private fun checkScratchProgressAsync(forceRevealCheck: Boolean = false) {
        if (isRevealed || isCheckingProgress) return
        val bmp = scratchBitmap ?: return
        if (bmp.isRecycled) return

        isCheckingProgress = true
        thread(start = true) {
            try {
                val w = bmp.width
                val h = bmp.height
                val sampleStep = 8
                var totalSampled = 0
                var clearedSampled = 0

                val pixels = IntArray(w * h)
                bmp.getPixels(pixels, 0, w, 0, 0, w, h)

                var y = 0
                while (y < h) {
                    var x = 0
                    while (x < w) {
                        totalSampled++
                        val pixel = pixels[y * w + x]
                        if (Color.alpha(pixel) < 30) {
                            clearedSampled++
                        }
                        x += sampleStep
                    }
                    y += sampleStep
                }

                val percent = if (totalSampled > 0) (clearedSampled.toFloat() / totalSampled) * 100f else 0f

                // If >38% is scratched, automatically reveal!
                if (percent >= 38f) {
                    post {
                        performRevealAnimation()
                    }
                }
            } catch (_: Exception) {
            } finally {
                isCheckingProgress = false
            }
        }
    }

    private fun performRevealAnimation() {
        if (isRevealed) return
        isRevealed = true

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40)
            }
        } catch (_: Exception) {}

        ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0f).apply {
            duration = 380
            interpolator = AccelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = View.GONE
                    scratchBitmap?.recycle()
                    scratchBitmap = null
                    onScratchRevealed?.invoke()
                }
            })
            start()
        }
    }

    fun revealInstantly() {
        isRevealed = true
        visibility = View.GONE
        scratchBitmap?.recycle()
        scratchBitmap = null
    }

    fun reset() {
        isRevealed = false
        alpha = 1f
        visibility = View.VISIBLE
        if (width > 0 && height > 0) {
            initScratchFoil(width, height)
            invalidate()
        }
    }
}
