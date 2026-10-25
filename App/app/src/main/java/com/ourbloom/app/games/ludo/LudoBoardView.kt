package com.ourbloom.app.games.ludo

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.hypot
import kotlin.math.min

class LudoBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var player1Tokens: List<Int> = listOf(-1, -1, -1, -1)
        set(value) {
            field = value
            invalidate()
        }

    var player2Tokens: List<Int> = listOf(-1, -1, -1, -1)
        set(value) {
            field = value
            invalidate()
        }

    var activePlayer: Int = 1
        set(value) {
            field = value
            invalidate()
        }

    var movableTokens: List<Int> = emptyList() // indices (0..3) of tokens of active player that can move
        set(value) {
            field = value
            invalidate()
        }

    var onTokenClickListener: ((Int) -> Unit)? = null

    var isFlipped: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    // 15x15 grid dimensions
    private var cellSize = 0f
    private val boardRect = RectF()
    private val cellRect = RectF()
    private val textBounds = Rect()

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFBFD") // Pristine Blush White
    }
    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0D0D8")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val p1BasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFDDE5") // Soft Rose Base
    }
    private val p1AccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E91E63") // Vibrant Rose Coral
    }
    private val p2BasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D7E9FB") // Soft Sky Base
    }
    private val p2AccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5") // Royal Sky Blue
    }
    private val decorativeYardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F9F0F5") // Frosted Lavender
    }
    private val safeSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF9C4") // Star Yellow
    }
    private val starTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F57F17")
        textAlign = Paint.Align.CENTER
        textSize = 28f
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#880E4F")
        textAlign = Paint.Align.CENTER
        textSize = 28f
        isFakeBoldText = true
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD54F")
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    // Token Paints
    private val p1TokenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E91E63")
        style = Paint.Style.FILL
    }
    private val p1TokenBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#880E4F")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val p2TokenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        style = Paint.Style.FILL
    }
    private val p2TokenBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D47A1")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val tokenSymbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 24f
    }

    // Shadow Paint for jumping tokens
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000") // 25% black soft shadow
        style = Paint.Style.FILL
    }
    private val shadowRect = RectF()

    // Pulse animation for movable token halos
    private var pulseFactor = 1f
    private val pulseAnimator = ValueAnimator.ofFloat(0.85f, 1.25f).apply {
        duration = 750
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulseFactor = it.animatedValue as Float
            if (movableTokens.isNotEmpty()) {
                invalidate()
            }
        }
    }

    // Token Jump Animation States
    var isAnimatingToken = false
        private set
    private var animatingPlayer = 1
    private var animatingTokenIndex = -1
    private var groundX = 0f
    private var groundY = 0f
    private var animatedX = 0f
    private var animatedY = 0f
    private var animatedScale = 1f
    private var animatedShadowScale = 1f
    private var animatedShadowAlpha = 0.35f
    private var currentMoveAnimator: ValueAnimator? = null

    // Capture Retreat Animation States
    var isCapturingAnim = false
        private set
    private var capturedPlayer = 2
    private var capturedTokenIndex = -1
    private var capturedX = 0f
    private var capturedY = 0f
    private var capturedScale = 1f
    private var currentCaptureAnimator: ValueAnimator? = null

    init {
        pulseAnimator.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (width > 0 && height > 0) min(width, height) else if (width > 0) width else height
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        cellSize = size / 15f
        starTextPaint.textSize = cellSize * 0.65f
        tokenSymbolPaint.textSize = cellSize * 0.45f
        labelTextPaint.textSize = cellSize * 0.45f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cellSize <= 0) return

        // 1. Background
        boardRect.set(0f, 0f, 15 * cellSize, 15 * cellSize)
        canvas.drawRect(boardRect, bgPaint)

        // 2. Draw Yards (6x6 corners)
        if (!isFlipped) {
            drawYard(canvas, 9, 0, p1BasePaint, p1AccentPaint, "Rose Hearts ♡", 1) // Bottom-Left (P1)
            drawYard(canvas, 0, 9, p2BasePaint, p2AccentPaint, "Sky Stars ⭐️", 2)   // Top-Right (P2)
            drawDecorativeYard(canvas, 0, 0, "Our Bloom 🌸")                       // Top-Left (Deco)
            drawDecorativeYard(canvas, 9, 9, "Love Castle 🏰")                     // Bottom-Right (Deco)
        } else {
            drawYard(canvas, 9, 0, p2BasePaint, p2AccentPaint, "Sky Stars ⭐️", 2)   // Bottom-Left (P2)
            drawYard(canvas, 0, 9, p1BasePaint, p1AccentPaint, "Rose Hearts ♡", 1) // Top-Right (P1)
            drawDecorativeYard(canvas, 0, 0, "Love Castle 🏰")                     // Top-Left (Deco)
            drawDecorativeYard(canvas, 9, 9, "Our Bloom 🌸")                       // Bottom-Right (Deco)
        }

        // 3. Draw Track Grid (15x15 cells)
        for (r in 0..14) {
            for (c in 0..14) {
                // Skip yard corners (6x6) and center (3x3)
                val inYard = (r < 6 && c < 6) || (r < 6 && c >= 9) || (r >= 9 && c < 6) || (r >= 9 && c >= 9)
                val inCenter = r in 6..8 && c in 6..8
                if (inYard || inCenter) continue

                val left = c * cellSize
                val top = r * cellSize
                cellRect.set(left, top, left + cellSize, top + cellSize)

                // Track colors: P1 Home Corridor (r=7, c=1..5), P2 Home Corridor (r=7, c=9..13)
                if (r == 7 && c in 1..5) {
                    canvas.drawRect(cellRect, if (!isFlipped) p1AccentPaint else p2AccentPaint)
                } else if (r == 7 && c in 9..13) {
                    canvas.drawRect(cellRect, if (!isFlipped) p2AccentPaint else p1AccentPaint)
                } else if (r == 6 && c == 1) {
                    // Start Cell next to bottom-left yard
                    canvas.drawRect(cellRect, if (!isFlipped) p1BasePaint else p2BasePaint)
                } else if (r == 8 && c == 13) {
                    // Start Cell next to top-right yard
                    canvas.drawRect(cellRect, if (!isFlipped) p2BasePaint else p1BasePaint)
                } else {
                    canvas.drawRect(cellRect, bgPaint)
                }

                // Grid border
                canvas.drawRect(cellRect, gridLinePaint)

                // Safe star markers
                val trackIndex = getTrackIndexForGridCell(r, c)
                if (trackIndex != null && LudoLogic.isSafeSquare(trackIndex)) {
                    val cx = left + cellSize / 2f
                    val cy = top + cellSize / 2f - (starTextPaint.descent() + starTextPaint.ascent()) / 2f
                    canvas.drawText("⭐️", cx, cy, starTextPaint)
                }
            }
        }

        // 4. Draw Center Castle (Heartland)
        drawCenterHeartland(canvas)

        // 5. Draw Tokens
        drawTokens(canvas)
    }

    private fun drawYard(canvas: Canvas, startRow: Int, startCol: Int, basePaint: Paint, accentPaint: Paint, title: String, player: Int) {
        val left = startCol * cellSize
        val top = startRow * cellSize
        val right = left + 6 * cellSize
        val bottom = top + 6 * cellSize

        // Outer base
        canvas.drawRoundRect(left + 6f, top + 6f, right - 6f, bottom - 6f, 24f, 24f, basePaint)
        // Inner white pad
        canvas.drawRoundRect(left + cellSize, top + cellSize, right - cellSize, bottom - cellSize, 20f, 20f, bgPaint)

        // 4 token slots inside yard
        val yardSlots = getYardSlotCoordinates(startRow, startCol)
        for ((cx, cy) in yardSlots) {
            canvas.drawCircle(cx, cy, cellSize * 0.7f, basePaint)
            canvas.drawCircle(cx, cy, cellSize * 0.65f, accentPaint)
        }
    }

    private fun drawDecorativeYard(canvas: Canvas, startRow: Int, startCol: Int, label: String) {
        val left = startCol * cellSize
        val top = startRow * cellSize
        val right = left + 6 * cellSize
        val bottom = top + 6 * cellSize

        canvas.drawRoundRect(left + 6f, top + 6f, right - 6f, bottom - 6f, 24f, 24f, decorativeYardPaint)
        val cx = left + 3 * cellSize
        val cy = top + 3 * cellSize
        canvas.drawText(label, cx, cy, labelTextPaint)
    }

    private fun drawCenterHeartland(canvas: Canvas) {
        val left = 6 * cellSize
        val top = 6 * cellSize
        val right = 9 * cellSize
        val bottom = 9 * cellSize
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f

        // 4 triangles meeting at center
        val path1 = Path().apply {
            moveTo(left, bottom)
            lineTo(cx, cy)
            lineTo(left, top)
            close()
        }
        canvas.drawPath(path1, if (!isFlipped) p1AccentPaint else p2AccentPaint)

        val path2 = Path().apply {
            moveTo(right, top)
            lineTo(cx, cy)
            lineTo(right, bottom)
            close()
        }
        canvas.drawPath(path2, if (!isFlipped) p2AccentPaint else p1AccentPaint)

        // Center Heart / Castle emblem
        val emblemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = cellSize * 0.9f
        }
        canvas.drawText("💖", cx, cy - (emblemPaint.descent() + emblemPaint.ascent()) / 2f, emblemPaint)
    }

    private fun drawTokens(canvas: Canvas) {
        // Draw Player 1 tokens
        for (i in player1Tokens.indices) {
            val pos = player1Tokens[i]
            if (isAnimatingToken && animatingPlayer == 1 && i == animatingTokenIndex) {
                // Draw shadow on ground
                val shadowW = cellSize * 0.42f * animatedShadowScale
                val shadowH = cellSize * 0.20f * animatedShadowScale
                shadowRect.set(groundX - shadowW, groundY - shadowH, groundX + shadowW, groundY + shadowH)
                shadowPaint.alpha = (animatedShadowAlpha * 255).toInt().coerceIn(0, 255)
                canvas.drawOval(shadowRect, shadowPaint)

                // Draw jumping token in air
                drawSingleToken(canvas, animatedX, animatedY, 1, "♡", false, animatedScale)
            } else if (isCapturingAnim && capturedPlayer == 1 && i == capturedTokenIndex) {
                drawSingleToken(canvas, capturedX, capturedY, 1, "♡", false, capturedScale)
            } else {
                val (cx, cy) = getTokenPositionCoordinates(pos, 1, i)
                val isMovable = !isAnimatingToken && !isCapturingAnim && activePlayer == 1 && movableTokens.contains(i)
                drawSingleToken(canvas, cx, cy, 1, "♡", isMovable, 1f)
            }
        }

        // Draw Player 2 tokens
        for (i in player2Tokens.indices) {
            val pos = player2Tokens[i]
            if (isAnimatingToken && animatingPlayer == 2 && i == animatingTokenIndex) {
                // Draw shadow on ground
                val shadowW = cellSize * 0.42f * animatedShadowScale
                val shadowH = cellSize * 0.20f * animatedShadowScale
                shadowRect.set(groundX - shadowW, groundY - shadowH, groundX + shadowW, groundY + shadowH)
                shadowPaint.alpha = (animatedShadowAlpha * 255).toInt().coerceIn(0, 255)
                canvas.drawOval(shadowRect, shadowPaint)

                // Draw jumping token in air
                drawSingleToken(canvas, animatedX, animatedY, 2, "★", false, animatedScale)
            } else if (isCapturingAnim && capturedPlayer == 2 && i == capturedTokenIndex) {
                drawSingleToken(canvas, capturedX, capturedY, 2, "★", false, capturedScale)
            } else {
                val (cx, cy) = getTokenPositionCoordinates(pos, 2, i)
                val isMovable = !isAnimatingToken && !isCapturingAnim && activePlayer == 2 && movableTokens.contains(i)
                drawSingleToken(canvas, cx, cy, 2, "★", isMovable, 1f)
            }
        }
    }

    private fun drawSingleToken(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        player: Int,
        symbol: String,
        isMovable: Boolean,
        scale: Float = 1f
    ) {
        val radius = cellSize * 0.42f

        canvas.save()
        if (scale != 1f) {
            canvas.scale(scale, scale, cx, cy)
        }

        if (isMovable) {
            // Pulsing selection halo
            canvas.drawCircle(cx, cy, radius * pulseFactor * 1.3f, haloPaint)
        }

        val fillPaint = if (player == 1) p1TokenPaint else p2TokenPaint
        val borderPaint = if (player == 1) p1TokenBorderPaint else p2TokenBorderPaint

        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        val textY = cy - (tokenSymbolPaint.descent() + tokenSymbolPaint.ascent()) / 2f
        canvas.drawText(symbol, cx, textY, tokenSymbolPaint)

        canvas.restore()
    }

    private fun getYardSlotCoordinates(startRow: Int, startCol: Int): List<Pair<Float, Float>> {
        val baseL = startCol * cellSize
        val baseT = startRow * cellSize
        return listOf(
            Pair(baseL + 2f * cellSize, baseT + 2f * cellSize),
            Pair(baseL + 4f * cellSize, baseT + 2f * cellSize),
            Pair(baseL + 2f * cellSize, baseT + 4f * cellSize),
            Pair(baseL + 4f * cellSize, baseT + 4f * cellSize)
        )
    }

    /**
     * Converts a token's position (in yard, on track, in corridor, or in home) into canvas (x, y).
     */
    fun getTokenPositionCoordinates(pos: Int, player: Int, tokenIndex: Int): Pair<Float, Float> {
        // In Yard
        if (pos == LudoLogic.YARD_POS) {
            val (startRow, startCol) = if (!isFlipped) {
                if (player == 1) Pair(9, 0) else Pair(0, 9)
            } else {
                if (player == 2) Pair(9, 0) else Pair(0, 9)
            }
            val slots = getYardSlotCoordinates(startRow, startCol)
            return slots.getOrElse(tokenIndex) { Pair(0f, 0f) }
        }

        // In Heartland Home (99)
        if (pos == LudoLogic.HOME_POS) {
            val cx = 7.5f * cellSize
            val cy = 7.5f * cellSize
            val angle = (tokenIndex * 90.0) + (if ((player == 1 && !isFlipped) || (player == 2 && isFlipped)) 0.0 else 45.0)
            val rad = Math.toRadians(angle)
            val dist = cellSize * 0.5f
            return Pair(cx + (dist * Math.cos(rad)).toFloat(), cy + (dist * Math.sin(rad)).toFloat())
        }

        // In Home Corridor (101..105 for P1, 201..205 for P2)
        if (pos > 100) {
            val step = pos % 100 // 1..5
            val isLeftCorridor = (!isFlipped && player == 1) || (isFlipped && player == 2)
            val c = if (isLeftCorridor) step else (14 - step)
            return Pair((c + 0.5f) * cellSize, 7.5f * cellSize)
        }

        // On Main Track (0..51)
        val gridPos = getGridCellForTrackIndex(pos)
        val cx = (gridPos.second + 0.5f) * cellSize
        val cy = (gridPos.first + 0.5f) * cellSize

        // Minor offset if multiple tokens share the square
        val offset = if (player == 2) cellSize * 0.12f else -cellSize * 0.12f
        return Pair(cx + offset, cy + offset)
    }

    /**
     * Map 52 track indices strictly CLOCKWISE into (row, col) on 15x15 grid.
     */
    private fun getGridCellForTrackIndex(idx: Int): Pair<Int, Int> {
        val effectiveIdx = if (isFlipped) (idx + 26) % 52 else idx
        return when (effectiveIdx % 52) {
            // Left Arm (top row of left arm, moving East towards center):
            0 -> Pair(6, 0)
            1 -> Pair(6, 1) // P1 Start Cell
            2 -> Pair(6, 2)
            3 -> Pair(6, 3)
            4 -> Pair(6, 4)
            5 -> Pair(6, 5)

            // Top Arm (left column of top arm, moving North towards edge):
            6 -> Pair(5, 6)
            7 -> Pair(4, 6)
            8 -> Pair(3, 6)
            9 -> Pair(2, 6) // Safe square (star)
            10 -> Pair(1, 6)
            11 -> Pair(0, 6)

            // Top edge:
            12 -> Pair(0, 7)
            13 -> Pair(0, 8)

            // Top Arm (right column of top arm, moving South towards center):
            14 -> Pair(1, 8) // Safe square (star)
            15 -> Pair(2, 8)
            16 -> Pair(3, 8)
            17 -> Pair(4, 8)
            18 -> Pair(5, 8)

            // Right Arm (top row of right arm, moving East towards edge):
            19 -> Pair(6, 9)
            20 -> Pair(6, 10)
            21 -> Pair(6, 11)
            22 -> Pair(6, 12) // Safe square (star)
            23 -> Pair(6, 13)
            24 -> Pair(6, 14)

            // Right edge:
            25 -> Pair(7, 14) // P2 Pre-Home Cell
            26 -> Pair(8, 14)

            // Right Arm (bottom row of right arm, moving West towards center):
            27 -> Pair(8, 13) // P2 Start Cell
            28 -> Pair(8, 12)
            29 -> Pair(8, 11)
            30 -> Pair(8, 10)
            31 -> Pair(8, 9)

            // Bottom Arm (right column of bottom arm, moving South towards edge):
            32 -> Pair(9, 8)
            33 -> Pair(10, 8)
            34 -> Pair(11, 8)
            35 -> Pair(12, 8) // Safe square (star)
            36 -> Pair(13, 8)
            37 -> Pair(14, 8)

            // Bottom edge:
            38 -> Pair(14, 7)
            39 -> Pair(14, 6)

            // Bottom Arm (left column of bottom arm, moving North towards center):
            40 -> Pair(13, 6) // Safe square (star)
            41 -> Pair(12, 6)
            42 -> Pair(11, 6)
            43 -> Pair(10, 6)
            44 -> Pair(9, 6)

            // Left Arm (bottom row of left arm, moving West towards edge):
            45 -> Pair(8, 5)
            46 -> Pair(8, 4)
            47 -> Pair(8, 3)
            48 -> Pair(8, 2) // Safe square (star)
            49 -> Pair(8, 1)
            50 -> Pair(8, 0)

            // Left edge:
            51 -> Pair(7, 0) // P1 Pre-Home Cell

            else -> Pair(6, 0)
        }
    }

    private fun getTrackIndexForGridCell(r: Int, c: Int): Int? {
        for (i in 0..51) {
            val cell = getGridCellForTrackIndex(i)
            if (cell.first == r && cell.second == c) return i
        }
        return null
    }

    /**
     * Executes a step-by-step jump animation along [path] with physical bounce, 3D arc, and ground shadow.
     */
    fun animateTokenMove(
        player: Int,
        tokenIndex: Int,
        path: List<Int>,
        capturedOpponentPlayer: Int? = null,
        capturedOpponentTokenIndex: Int? = null,
        onStepHop: (() -> Unit)? = null,
        onComplete: () -> Unit
    ) {
        if (path.isEmpty()) {
            onComplete()
            return
        }

        currentMoveAnimator?.cancel()
        currentCaptureAnimator?.cancel()

        val currentTokens = if (player == 1) player1Tokens else player2Tokens
        val startPos = currentTokens[tokenIndex]

        isAnimatingToken = true
        animatingPlayer = player
        this.animatingTokenIndex = tokenIndex

        val totalHops = path.size
        val hopDuration = 160L // Snappy, joyful hop

        currentMoveAnimator = ValueAnimator.ofFloat(0f, totalHops.toFloat()).apply {
            duration = totalHops * hopDuration
            interpolator = LinearInterpolator()
            var currentHopIndex = -1

            addUpdateListener { va ->
                val progress = va.animatedValue as Float
                val hopIdx = progress.toInt().coerceIn(0, totalHops - 1)
                val hopFraction = (progress - hopIdx).coerceIn(0f, 1f)

                if (hopIdx != currentHopIndex) {
                    currentHopIndex = hopIdx
                    onStepHop?.invoke()
                }

                val fromPos = if (hopIdx == 0) startPos else path[hopIdx - 1]
                val toPos = path[hopIdx]

                val fromCoord = getTokenPositionCoordinates(fromPos, player, tokenIndex)
                val toCoord = getTokenPositionCoordinates(toPos, player, tokenIndex)

                groundX = fromCoord.first + (toCoord.first - fromCoord.first) * hopFraction
                groundY = fromCoord.second + (toCoord.second - fromCoord.second) * hopFraction

                val jumpArc = Math.sin(hopFraction * Math.PI).toFloat()
                val jumpHeight = jumpArc * cellSize * 0.55f

                animatedX = groundX
                animatedY = groundY - jumpHeight
                animatedScale = 1f + jumpArc * 0.32f
                animatedShadowScale = 1f - jumpArc * 0.35f
                animatedShadowAlpha = 0.35f - jumpArc * 0.15f

                invalidate()
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimatingToken = false
                    currentMoveAnimator = null

                    // Final landing step tick
                    onStepHop?.invoke()

                    val finalPos = path.last()
                    val finalCoord = getTokenPositionCoordinates(finalPos, player, tokenIndex)

                    if (capturedOpponentPlayer != null && capturedOpponentTokenIndex != null) {
                        animateCaptureRetreat(
                            capturedPlayer = capturedOpponentPlayer,
                            capturedTokenIndex = capturedOpponentTokenIndex,
                            startCoord = finalCoord,
                            onComplete = {
                                invalidate()
                                onComplete()
                            }
                        )
                    } else {
                        invalidate()
                        onComplete()
                    }
                }
            })
        }
        currentMoveAnimator?.start()
    }

    private fun animateCaptureRetreat(
        capturedPlayer: Int,
        capturedTokenIndex: Int,
        startCoord: Pair<Float, Float>,
        onComplete: () -> Unit
    ) {
        val (startRow, startCol) = if (!isFlipped) {
            if (capturedPlayer == 1) Pair(9, 0) else Pair(0, 9)
        } else {
            if (capturedPlayer == 2) Pair(9, 0) else Pair(0, 9)
        }
        val yardSlots = getYardSlotCoordinates(startRow, startCol)
        val targetCoord = yardSlots.getOrElse(capturedTokenIndex) { Pair(0f, 0f) }

        isCapturingAnim = true
        this.capturedPlayer = capturedPlayer
        this.capturedTokenIndex = capturedTokenIndex

        currentCaptureAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380L
            interpolator = AccelerateInterpolator()
            addUpdateListener { va ->
                val f = va.animatedValue as Float
                capturedX = startCoord.first + (targetCoord.first - startCoord.first) * f
                val arc = Math.sin(f * Math.PI).toFloat() * cellSize * 0.75f
                capturedY = startCoord.second + (targetCoord.second - startCoord.second) * f - arc
                capturedScale = 1f + Math.sin(f * Math.PI).toFloat() * 0.25f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isCapturingAnim = false
                    currentCaptureAnimator = null
                    invalidate()
                    onComplete()
                }
            })
        }
        currentCaptureAnimator?.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isAnimatingToken || isCapturingAnim) {
            return false
        }

        if (event.action == MotionEvent.ACTION_UP && movableTokens.isNotEmpty()) {
            val touchX = event.x
            val touchY = event.y

            val currentTokens = if (activePlayer == 1) player1Tokens else player2Tokens

            // Check if user tapped near any movable token
            var clickedTokenIndex = -1
            var minDistance = Float.MAX_VALUE

            for (tokenIdx in movableTokens) {
                val (cx, cy) = getTokenPositionCoordinates(currentTokens[tokenIdx], activePlayer, tokenIdx)
                val dist = hypot((touchX - cx).toDouble(), (touchY - cy).toDouble()).toFloat()
                if (dist < cellSize * 1.1f && dist < minDistance) {
                    minDistance = dist
                    clickedTokenIndex = tokenIdx
                }
            }

            if (clickedTokenIndex != -1) {
                onTokenClickListener?.invoke(clickedTokenIndex)
                return true
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator.cancel()
        currentMoveAnimator?.cancel()
        currentMoveAnimator = null
        currentCaptureAnimator?.cancel()
        currentCaptureAnimator = null
    }
}
