package com.ourbloom.app.touch

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class ThumbKissCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onLocalTouchChanged: ((xRatio: Float, yRatio: Float, isTouching: Boolean) -> Unit)? = null
    var onKissContactStateChanged: ((isInContact: Boolean) -> Unit)? = null
    var onKissCollisionChanged: ((isKissing: Boolean) -> Unit)? = null

    // Local touch coordinates
    private var localX = -1f
    private var localY = -1f
    var isLocalTouching = false
        private set

    // Partner touch coordinates (in screen pixels)
    private var partnerX = -1f
    private var partnerY = -1f
    var isPartnerTouching = false
        private set

    var isRemotePartnerActive = false
        private set

    private var pulsePhase = 0f
    private val particles = mutableListOf<PetalParticle>()
    private val maxParticles = 80

    private val localPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val partnerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    data class PetalParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        var alpha: Int,
        var rotation: Float,
        var rotSpeed: Float,
        val color: Int
    )

    init {
        setWillNotDraw(false)
    }

    fun updatePartnerTouch(xRatio: Float, yRatio: Float, isTouching: Boolean) {
        isRemotePartnerActive = isTouching
        isPartnerTouching = isTouching
        partnerX = xRatio * width
        partnerY = yRatio * height

        checkContactState()
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                isLocalTouching = true
                localX = event.getX(0)
                localY = event.getY(0)

                // When testing solo with 2 fingers and no remote partner is active, finger 1 acts as partner touch
                if (event.pointerCount >= 2 && !isRemotePartnerActive) {
                    partnerX = event.getX(1)
                    partnerY = event.getY(1)
                    isPartnerTouching = true
                } else if (!isRemotePartnerActive) {
                    isPartnerTouching = false
                }

                val xRatio = if (width > 0) localX / width else 0.5f
                val yRatio = if (height > 0) localY / height else 0.5f
                onLocalTouchChanged?.invoke(xRatio, yRatio, true)
                checkContactState()
                postInvalidateOnAnimation()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2 && !isRemotePartnerActive) {
                    partnerX = event.getX(1)
                    partnerY = event.getY(1)
                    isPartnerTouching = true
                    checkContactState()
                    postInvalidateOnAnimation()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                if (index == 1 && !isRemotePartnerActive) {
                    isPartnerTouching = false
                    checkContactState()
                    postInvalidateOnAnimation()
                } else if (index == 0 && event.pointerCount >= 2 && !isRemotePartnerActive) {
                    localX = event.getX(1)
                    localY = event.getY(1)
                    isPartnerTouching = false
                    val xRatio = if (width > 0) localX / width else 0.5f
                    val yRatio = if (height > 0) localY / height else 0.5f
                    onLocalTouchChanged?.invoke(xRatio, yRatio, true)
                    checkContactState()
                    postInvalidateOnAnimation()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isLocalTouching = false
                if (!isRemotePartnerActive) {
                    isPartnerTouching = false
                }
                val xRatio = if (width > 0) localX / width else 0.5f
                val yRatio = if (height > 0) localY / height else 0.5f
                onLocalTouchChanged?.invoke(xRatio, yRatio, false)
                checkContactState()
                postInvalidateOnAnimation()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private var wasContact = false
    private var wasKissing = false

    private fun checkContactState() {
        val isContact = isLocalTouching && isPartnerTouching
        if (isContact != wasContact) {
            wasContact = isContact
            onKissContactStateChanged?.invoke(isContact)
        }

        if (isContact) {
            val dist = hypot((partnerX - localX).toDouble(), (partnerY - localY).toDouble()).toFloat()
            val isKissing = dist < 220f
            if (isKissing != wasKissing) {
                wasKissing = isKissing
                if (isKissing) {
                    spawnBurst((localX + partnerX) / 2f, (localY + partnerY) / 2f, 25)
                }
                onKissCollisionChanged?.invoke(isKissing)
            }
        } else {
            if (wasKissing) {
                wasKissing = false
                onKissCollisionChanged?.invoke(false)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        pulsePhase += 0.06f
        val pulseFactor = (sin(pulsePhase) * 0.15f) + 1.0f

        // 1. Draw connecting beam if both are touching
        if (isLocalTouching && isPartnerTouching) {
            val dist = hypot((partnerX - localX).toDouble(), (partnerY - localY).toDouble()).toFloat()
            val midX = (localX + partnerX) / 2f
            val midY = (localY + partnerY) / 2f

            beamPaint.shader = LinearGradient(
                localX, localY, partnerX, partnerY,
                intArrayOf(Color.parseColor("#FF5E8E"), Color.parseColor("#FFD2DD"), Color.parseColor("#9D4EDD")),
                null, Shader.TileMode.CLAMP
            )
            beamPaint.strokeWidth = (12f * pulseFactor).coerceIn(6f, 24f)
            beamPaint.alpha = (180 * (pulseFactor * 0.8f)).toInt().coerceIn(80, 240)

            val path = Path()
            path.moveTo(localX, localY)
            val curveOffset = sin(pulsePhase * 1.5f) * 40f
            path.quadTo(midX + curveOffset, midY - curveOffset, partnerX, partnerY)
            canvas.drawPath(path, beamPaint)

            // Spawn particles at touch points or midpoint
            if (particles.size < maxParticles && Random.nextFloat() < 0.6f) {
                spawnParticle(midX, midY)
            }
        }

        // 2. Draw partner's aura
        if (isPartnerTouching && partnerX >= 0 && partnerY >= 0) {
            val radius = 130f * pulseFactor
            partnerPaint.shader = RadialGradient(
                partnerX, partnerY, radius,
                intArrayOf(
                    Color.parseColor("#CC9D4EDD"),
                    Color.parseColor("#667B2CBF"),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(partnerX, partnerY, radius, partnerPaint)

            val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFF0F5")
                alpha = (220 * pulseFactor).toInt().coerceIn(120, 255)
            }
            canvas.drawCircle(partnerX, partnerY, 20f * pulseFactor, innerPaint)

            if (particles.size < maxParticles && Random.nextFloat() < 0.3f) {
                spawnParticle(partnerX, partnerY)
            }
        }

        // 3. Draw local aura
        if (isLocalTouching && localX >= 0 && localY >= 0) {
            val radius = 130f * pulseFactor
            localPaint.shader = RadialGradient(
                localX, localY, radius,
                intArrayOf(
                    Color.parseColor("#CCFF4D80"),
                    Color.parseColor("#66FF85A2"),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(localX, localY, radius, localPaint)

            val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFFFFF")
                alpha = (240 * pulseFactor).toInt().coerceIn(150, 255)
            }
            canvas.drawCircle(localX, localY, 22f * pulseFactor, innerPaint)

            if (particles.size < maxParticles && Random.nextFloat() < 0.3f) {
                spawnParticle(localX, localY)
            }
        }

        // 4. Update and draw particles
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            p.y += p.vy
            p.rotation += p.rotSpeed
            p.alpha = (p.alpha - 3).coerceAtLeast(0)

            if (p.alpha <= 0 || p.y < -50 || p.y > height + 50 || p.x < -50 || p.x > width + 50) {
                iterator.remove()
                continue
            }

            particlePaint.color = p.color
            particlePaint.alpha = p.alpha

            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rotation)
            // Draw a cute heart or petal oval
            val petalRect = RectF(-p.size / 2f, -p.size, p.size / 2f, p.size)
            canvas.drawOval(petalRect, particlePaint)
            canvas.restore()
        }

        // Animate continuously while touching or particles alive
        if (isLocalTouching || isPartnerTouching || particles.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }

    private fun spawnParticle(originX: Float, originY: Float) {
        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
        val speed = Random.nextFloat() * 4f + 1.5f
        val colors = intArrayOf(
            Color.parseColor("#FF5E8E"),
            Color.parseColor("#FF85A2"),
            Color.parseColor("#FFD2DD"),
            Color.parseColor("#D47AE8")
        )
        val particle = PetalParticle(
            x = originX + (Random.nextFloat() - 0.5f) * 40f,
            y = originY + (Random.nextFloat() - 0.5f) * 40f,
            vx = cos(angle) * speed,
            vy = sin(angle) * speed - 1.2f, // float upward
            size = Random.nextFloat() * 12f + 8f,
            alpha = 240,
            rotation = Random.nextFloat() * 360f,
            rotSpeed = (Random.nextFloat() - 0.5f) * 6f,
            color = colors[Random.nextInt(colors.size)]
        )
        particles.add(particle)
    }

    fun spawnBurst(originX: Float, originY: Float, count: Int = 25) {
        for (i in 0 until count) {
            spawnParticle(originX, originY)
        }
        postInvalidateOnAnimation()
    }
}
