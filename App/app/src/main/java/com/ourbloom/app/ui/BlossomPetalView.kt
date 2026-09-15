package com.ourbloom.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class BlossomPetalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val petals = mutableListOf<Petal>()
    private var maxPetals = 5
    private var isRunning = true
    private val petalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val colors = intArrayOf(
        Color.parseColor("#FFF0F5"), // Lavender blush
        Color.parseColor("#FFD2DD"), // Soft sakura
        Color.parseColor("#FFCCD5"), // Romantic blossom
        Color.parseColor("#E85D75"), // Petal rose
        Color.parseColor("#FFB3C6")  // Warm pink
    )

    private data class Petal(
        var x: Float,
        var y: Float,
        var vy: Float,
        var vx: Float,
        var size: Float,
        var rotation: Float,
        var rotSpeed: Float,
        var swayPhase: Float,
        var swaySpeed: Float,
        var swayAmplitude: Float,
        var flipPhase: Float,
        var flipSpeed: Float,
        val color: Int,
        val baseAlpha: Int
    )

    init {
        setWillNotDraw(false)
        if (isReducedMotion()) {
            isRunning = false
        }
    }

    fun setPetalCount(count: Int) {
        maxPetals = count.coerceIn(0, 15)
        if (width > 0 && height > 0) {
            initPetals(width, height)
            invalidate()
        }
    }

    private fun isReducedMotion(): Boolean {
        return try {
            val durationScale = android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            durationScale == 0f
        } catch (_: Throwable) {
            false
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && petals.isEmpty() && isRunning) {
            initPetals(w, h)
        }
    }

    private fun initPetals(w: Int, h: Int) {
        petals.clear()
        for (i in 0 until maxPetals) {
            petals.add(createPetal(w, h, randomizeY = true))
        }
    }

    private fun createPetal(w: Int, h: Int, randomizeY: Boolean = false): Petal {
        val y = if (randomizeY) Random.nextFloat() * h else -25f - Random.nextFloat() * 50f
        // 85% of petals placed strictly in outer 15% margin rails so center content remains clear
        val x = if (Random.nextFloat() < 0.85f) {
            if (Random.nextBoolean()) {
                Random.nextFloat() * (w * 0.15f)
            } else {
                w * 0.85f + Random.nextFloat() * (w * 0.15f)
            }
        } else {
            Random.nextFloat() * w
        }
        val size = Random.nextFloat() * 6f + 11f // 11 to 17 dp
        return Petal(
            x = x,
            y = y,
            vy = Random.nextFloat() * 0.45f + 0.25f, // Serene, meditative drift
            vx = (Random.nextFloat() - 0.5f) * 0.2f,
            size = size,
            rotation = Random.nextFloat() * 360f,
            rotSpeed = (Random.nextFloat() - 0.5f) * 0.6f,
            swayPhase = Random.nextFloat() * 6.28f,
            swaySpeed = Random.nextFloat() * 0.018f + 0.008f,
            swayAmplitude = Random.nextFloat() * 0.8f + 0.4f,
            flipPhase = Random.nextFloat() * 6.28f,
            flipSpeed = Random.nextFloat() * 0.02f + 0.01f,
            color = colors[Random.nextInt(colors.size)],
            baseAlpha = Random.nextInt(20, 50) // Faint, translucent whisper
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || !isRunning) return

        val w = width.toFloat()
        val h = height.toFloat()

        for (p in petals) {
            // Physics update
            p.swayPhase += p.swaySpeed
            p.flipPhase += p.flipSpeed
            p.rotation += p.rotSpeed

            val sway = sin(p.swayPhase) * p.swayAmplitude
            p.x += p.vx + sway
            p.y += p.vy

            // Recycle if fallen below screen or drifted too far
            if (p.y > h + 50f || p.x < -60f || p.x > w + 60f) {
                p.y = -30f - Random.nextFloat() * 40f
                p.x = if (Random.nextFloat() < 0.85f) {
                    if (Random.nextBoolean()) {
                        Random.nextFloat() * (w * 0.15f)
                    } else {
                        w * 0.85f + Random.nextFloat() * (w * 0.15f)
                    }
                } else {
                    Random.nextFloat() * w
                }
                p.rotation = Random.nextFloat() * 360f
            }

            // 3D-like perspective scaling (flutter effect)
            val flutterScaleX = cos(p.flipPhase)

            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rotation)
            canvas.scale(flutterScaleX, 1f)

            petalPaint.color = p.color
            petalPaint.alpha = p.baseAlpha

            // Draw organic cherry blossom petal path
            val halfW = p.size * 0.48f
            val halfH = p.size * 0.72f
            val rect = RectF(-halfW, -halfH, halfW, halfH)
            canvas.drawOval(rect, petalPaint)

            canvas.restore()
        }

        if (isRunning) {
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
            val touchX = event.x
            val touchY = event.y
            // Gentle breeze push away from touch
            for (p in petals) {
                val dx = p.x - touchX
                val dy = p.y - touchY
                val distSq = dx * dx + dy * dy
                if (distSq < 30000f) { // ~170px radius
                    p.vx += (dx / 300f)
                    p.vy -= 0.6f
                    p.rotSpeed += (dx / 100f)
                }
            }
        }
        return false // Don't consume so parent scroll still works
    }

    fun pause() {
        isRunning = false
    }

    fun resume() {
        if (!isRunning) {
            isRunning = true
            postInvalidateOnAnimation()
        }
    }

    fun pauseAnimation() = pause()
    fun resumeAnimation() = resume()

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) resume() else pause()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) resume() else pause()
    }
}
