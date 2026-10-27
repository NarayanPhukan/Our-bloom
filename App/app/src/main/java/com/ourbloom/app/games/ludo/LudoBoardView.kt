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

    // Color Palette matching authentic Ludo reference
    companion object {
        const val COLOR_BLUE = 0xFF008CE4.toInt()
        const val COLOR_BLUE_DARK = 0xFF0067A8.toInt()
        const val COLOR_BLUE_LIGHT = 0xFFE1F2FC.toInt()

        const val COLOR_YELLOW = 0xFFFFCC00.toInt()
        const val COLOR_YELLOW_DARK = 0xFFCC9900.toInt()
        const val COLOR_YELLOW_LIGHT = 0xFFFFF9C4.toInt()

        const val COLOR_GREEN = 0xFF009B48.toInt()
        const val COLOR_GREEN_DARK = 0xFF007335.toInt()
        const val COLOR_GREEN_LIGHT = 0xFFE8F5E9.toInt()

        const val COLOR_RED = 0xFFE52521.toInt()
        const val COLOR_RED_DARK = 0xFFB31815.toInt()
        const val COLOR_RED_LIGHT = 0xFFFFEBEE.toInt()

        const val COLOR_GRID_LINE = 0xFFCCCCCC.toInt()
    }

    // Core Board Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_GRID_LINE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val innerPadBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // Quadrant Paints
    private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BLUE }
    private val blueDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BLUE_DARK }
    private val yellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_YELLOW }
    private val yellowDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_YELLOW_DARK }
    private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_GREEN }
    private val greenDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_GREEN_DARK }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_RED }
    private val redDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_RED_DARK }

    // Star & Arrow Paints
    private val starOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF616161.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeJoin = Paint.Join.ROUND
    }
    private val whiteStarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeJoin = Paint.Join.ROUND
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD54F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    // Token Paints (P1 = Red / Rose Hearts, P2 = Yellow / Golden Stars)
    private val p1TokenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_RED
        style = Paint.Style.FILL
    }
    private val p1TokenBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_RED_DARK
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val p2TokenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_YELLOW
        style = Paint.Style.FILL
    }
    private val p2TokenBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_YELLOW_DARK
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val tokenSymbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 24f
        isFakeBoldText = true
    }
    private val p2TokenSymbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5D4037.toInt() // Rich deep amber brown for crisp contrast on yellow
        textAlign = Paint.Align.CENTER
        textSize = 24f
        isFakeBoldText = true
    }

    // Shadow Paint for jumping tokens
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40000000 // 25% black soft shadow
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
        tokenSymbolPaint.textSize = cellSize * 0.45f
        p2TokenSymbolPaint.textSize = cellSize * 0.45f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cellSize <= 0) return

        // 1. Board Background
        boardRect.set(0f, 0f, 15 * cellSize, 15 * cellSize)
        canvas.drawRect(boardRect, bgPaint)

        // 2. Draw 4 Corner Yards (6x6 each)
        drawAllYards(canvas)

        // 3. Draw Track Grid (15x15 with home corridors, start cells, arrows, and safe stars)
        drawTrackGrid(canvas)

        // 4. Draw Center (3x3 with 4 meeting triangles)
        drawCenterTriangles(canvas)

        // 5. Draw Active Tokens (P1 = Red, P2 = Yellow)
        drawTokens(canvas)
    }

    /**
     * Draws the 4 authentic Ludo corner bases:
     * Top-Left: Blue, Top-Right: Yellow, Bottom-Right: Green, Bottom-Left: Red.
     * When flipped 180°: Top-Left becomes Green, Top-Right becomes Red, Bottom-Right becomes Blue, Bottom-Left becomes Yellow.
     */
    private fun drawAllYards(canvas: Canvas) {
        if (!isFlipped) {
            // Standard perspective: Red (P1) at Bottom-Left, Yellow (P2) at Top-Right
            drawSingleYard(canvas, 0, 0, bluePaint, blueDarkPaint)     // Top-Left (Blue)
            drawSingleYard(canvas, 0, 9, yellowPaint, yellowDarkPaint) // Top-Right (Yellow - P2)
            drawSingleYard(canvas, 9, 9, greenPaint, greenDarkPaint)   // Bottom-Right (Green)
            drawSingleYard(canvas, 9, 0, redPaint, redDarkPaint)       // Bottom-Left (Red - P1)
        } else {
            // 180° Rotated perspective: Yellow (P2) at Bottom-Left, Red (P1) at Top-Right
            drawSingleYard(canvas, 0, 0, greenPaint, greenDarkPaint)   // Top-Left (Green)
            drawSingleYard(canvas, 0, 9, redPaint, redDarkPaint)       // Top-Right (Red - P1)
            drawSingleYard(canvas, 9, 9, bluePaint, blueDarkPaint)     // Bottom-Right (Blue)
            drawSingleYard(canvas, 9, 0, yellowPaint, yellowDarkPaint) // Bottom-Left (Yellow - P2)
        }
    }

    private fun drawSingleYard(
        canvas: Canvas,
        startRow: Int,
        startCol: Int,
        framePaint: Paint,
        pocketBorderPaint: Paint
    ) {
        val left = startCol * cellSize
        val top = startRow * cellSize
        val right = left + 6 * cellSize
        val bottom = top + 6 * cellSize

        // Outer colored yard frame (6x6)
        canvas.drawRoundRect(left + 2f, top + 2f, right - 2f, bottom - 2f, 16f, 16f, framePaint)

        // Inner white pad (4x4)
        val padL = left + cellSize
        val padT = top + cellSize
        val padR = right - cellSize
        val padB = bottom - cellSize
        canvas.drawRoundRect(padL, padT, padR, padB, 18f, 18f, bgPaint)
        canvas.drawRoundRect(padL, padT, padR, padB, 18f, 18f, innerPadBorderPaint)

        // 4 circular token pockets
        val yardSlots = getYardSlotCoordinates(startRow, startCol)
        for ((cx, cy) in yardSlots) {
            // Shadow / dark bevel ring
            canvas.drawCircle(cx, cy, cellSize * 0.58f, pocketBorderPaint)
            // Raised color spot
            canvas.drawCircle(cx, cy, cellSize * 0.50f, framePaint)
        }
    }

    /**
     * Draws the main 15x15 grid tracks, authentic home corridors, start tiles, arrows, and stars.
     */
    private fun drawTrackGrid(canvas: Canvas) {
        // Safe square positions in standard (r, c)
        // 1: (6, 1) Blue start
        // 9: (2, 6) Top arm
        // 14: (1, 8) Yellow start
        // 22: (6, 12) Right arm
        // 27: (8, 13) Green start
        // 35: (12, 8) Bottom arm
        // 40: (13, 6) Red start
        // 48: (8, 2) Left arm

        val pBlue = if (!isFlipped) bluePaint else greenPaint
        val pYellow = if (!isFlipped) yellowPaint else redPaint
        val pGreen = if (!isFlipped) greenPaint else bluePaint
        val pRed = if (!isFlipped) redPaint else yellowPaint

        for (r in 0..14) {
            for (c in 0..14) {
                // Skip yard corners (6x6) and center (3x3)
                val inYard = (r < 6 && c < 6) || (r < 6 && c >= 9) || (r >= 9 && c < 6) || (r >= 9 && c >= 9)
                val inCenter = r in 6..8 && c in 6..8
                if (inYard || inCenter) continue

                val left = c * cellSize
                val top = r * cellSize
                cellRect.set(left, top, left + cellSize, top + cellSize)
                val cx = left + cellSize / 2f
                val cy = top + cellSize / 2f

                // Cell background coloring:
                when {
                    // Left Arm Home Corridor (Blue in normal, Green in flipped)
                    r == 7 && c in 1..5 -> canvas.drawRect(cellRect, pBlue)
                    // Top Arm Home Corridor (Yellow in normal, Red in flipped)
                    c == 7 && r in 1..5 -> canvas.drawRect(cellRect, pYellow)
                    // Right Arm Home Corridor (Green in normal, Blue in flipped)
                    r == 7 && c in 9..13 -> canvas.drawRect(cellRect, pGreen)
                    // Bottom Arm Home Corridor (Red in normal, Yellow in flipped)
                    c == 7 && r in 9..13 -> canvas.drawRect(cellRect, pRed)

                    // Start Squares
                    r == 6 && c == 1 -> canvas.drawRect(cellRect, pBlue)    // Blue start
                    r == 1 && c == 8 -> canvas.drawRect(cellRect, pYellow)  // Yellow start
                    r == 8 && c == 13 -> canvas.drawRect(cellRect, pGreen)  // Green start
                    r == 13 && c == 6 -> canvas.drawRect(cellRect, pRed)    // Red start

                    else -> canvas.drawRect(cellRect, bgPaint)
                }

                // Cell Border
                canvas.drawRect(cellRect, gridLinePaint)

                // Entry Directional Arrows at the entry of each home corridor
                when {
                    r == 7 && c == 0 -> drawArrow(canvas, cx, cy, 0, pBlue)    // → pointing East
                    r == 0 && c == 7 -> drawArrow(canvas, cx, cy, 1, pYellow)  // ↓ pointing South
                    r == 7 && c == 14 -> drawArrow(canvas, cx, cy, 2, pGreen)  // ← pointing West
                    r == 14 && c == 7 -> drawArrow(canvas, cx, cy, 3, pRed)    // ↑ pointing North
                }

                // 8 Authentic Safe Star Squares (outlined star)
                val isSafeCell = (r == 6 && c == 1) || (r == 2 && c == 6) ||
                                 (r == 1 && c == 8) || (r == 6 && c == 12) ||
                                 (r == 8 && c == 13) || (r == 12 && c == 8) ||
                                 (r == 13 && c == 6) || (r == 8 && c == 2)

                if (isSafeCell) {
                    val isStartColored = (r == 6 && c == 1) || (r == 1 && c == 8) ||
                                         (r == 8 && c == 13) || (r == 13 && c == 6)
                    drawOutlinedStar(canvas, cx, cy, cellSize * 0.38f, isWhite = isStartColored)
                }
            }
        }
    }

    /**
     * Draws the 4 classic Ludo triangles meeting at the center point (7.5, 7.5).
     */
    private fun drawCenterTriangles(canvas: Canvas) {
        val left = 6 * cellSize
        val top = 6 * cellSize
        val right = 9 * cellSize
        val bottom = 9 * cellSize
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f

        val pBlue = if (!isFlipped) bluePaint else greenPaint
        val pYellow = if (!isFlipped) yellowPaint else redPaint
        val pGreen = if (!isFlipped) greenPaint else bluePaint
        val pRed = if (!isFlipped) redPaint else yellowPaint

        // Left Triangle (Blue)
        val pathLeft = Path().apply {
            moveTo(left, top)
            lineTo(cx, cy)
            lineTo(left, bottom)
            close()
        }
        canvas.drawPath(pathLeft, pBlue)

        // Top Triangle (Yellow)
        val pathTop = Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(cx, cy)
            close()
        }
        canvas.drawPath(pathTop, pYellow)

        // Right Triangle (Green)
        val pathRight = Path().apply {
            moveTo(right, top)
            lineTo(right, bottom)
            lineTo(cx, cy)
            close()
        }
        canvas.drawPath(pathRight, pGreen)

        // Bottom Triangle (Red)
        val pathBottom = Path().apply {
            moveTo(left, bottom)
            lineTo(cx, cy)
            lineTo(right, bottom)
            close()
        }
        canvas.drawPath(pathBottom, pRed)

        // Triangle separating borders
        canvas.drawLine(left, top, right, bottom, gridLinePaint)
        canvas.drawLine(left, bottom, right, top, gridLinePaint)
        canvas.drawRect(left, top, right, bottom, gridLinePaint)

        // Center Heartland Emblem (Our Bloom Romantic Sparkle)
        val emblemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = cellSize * 0.75f
        }
        canvas.drawText("💖", cx, cy - (emblemPaint.descent() + emblemPaint.ascent()) / 2f, emblemPaint)
    }

    /**
     * Draws an outlined 5-pointed star matching the reference image.
     */
    private fun drawOutlinedStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, isWhite: Boolean) {
        val path = Path()
        val innerRadius = radius * 0.44f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) radius else innerRadius
            val angle = Math.toRadians((i * 36.0) - 90.0)
            val x = cx + (r * Math.cos(angle)).toFloat()
            val y = cy + (r * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        val paint = if (isWhite) whiteStarPaint else starOutlinePaint
        canvas.drawPath(path, paint)
    }

    /**
     * Draws a clean directional arrow vector pointing towards home.
     * direction: 0 = East (→), 1 = South (↓), 2 = West (←), 3 = North (↑)
     */
    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, direction: Int, paint: Paint) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(direction * 90f)
        val s = cellSize * 0.36f
        val path = Path().apply {
            // Arrow pointing East (0°)
            moveTo(s * 0.85f, 0f)
            lineTo(-s * 0.3f, -s * 0.65f)
            lineTo(-s * 0.1f, -s * 0.22f)
            lineTo(-s * 0.75f, -s * 0.22f)
            lineTo(-s * 0.75f, s * 0.22f)
            lineTo(-s * 0.1f, s * 0.22f)
            lineTo(-s * 0.3f, s * 0.65f)
            close()
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawTokens(canvas: Canvas) {
        // Draw Player 1 tokens (Red / Rose Hearts ♡)
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

        // Draw Player 2 tokens (Yellow / Golden Stars ⭐️)
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
            canvas.drawCircle(cx, cy, radius * pulseFactor * 1.35f, haloPaint)
        }

        val fillPaint = if (player == 1) p1TokenPaint else p2TokenPaint
        val borderPaint = if (player == 1) p1TokenBorderPaint else p2TokenBorderPaint
        val symPaint = if (player == 1) tokenSymbolPaint else p2TokenSymbolPaint

        // Subtle 3D bottom bevel/shadow
        shadowRect.set(cx - radius * 0.95f, cy - radius * 0.7f, cx + radius * 0.95f, cy + radius * 1.15f)
        canvas.drawOval(shadowRect, shadowPaint)

        // Main token body
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // Inner circular highlight ring for tactile 3D look
        val innerHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = if (player == 1) 0x60FFFFFF else 0x40FFFFFF
        }
        canvas.drawCircle(cx, cy, radius * 0.75f, innerHighlight)

        val textY = cy - (symPaint.descent() + symPaint.ascent()) / 2f
        canvas.drawText(symbol, cx, textY, symPaint)

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
     * Player 1 = Red (Bottom-Left: yard (9, 0), start 40, home corridor bottom vertical).
     * Player 2 = Yellow (Top-Right: yard (0, 9), start 14, home corridor top vertical).
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
            val isBottom = (!isFlipped && player == 1) || (isFlipped && player == 2)
            val baseCy = if (isBottom) (cy + cellSize * 0.55f) else (cy - cellSize * 0.55f)

            // Cluster the 4 tokens comfortably inside their colored triangle
            val offsetX = when (tokenIndex) {
                0 -> -cellSize * 0.35f
                1 -> cellSize * 0.35f
                2 -> -cellSize * 0.18f
                else -> cellSize * 0.18f
            }
            val offsetY = when (tokenIndex) {
                0, 1 -> if (isBottom) cellSize * 0.25f else -cellSize * 0.25f
                else -> if (isBottom) -cellSize * 0.15f else cellSize * 0.15f
            }
            return Pair(cx + offsetX, baseCy + offsetY)
        }

        // In Home Corridor (101..105 for P1 Red, 201..205 for P2 Yellow)
        if (pos > 100) {
            val step = pos % 100 // 1..5
            val isBottomCorridor = (!isFlipped && player == 1) || (isFlipped && player == 2)
            val row = if (isBottomCorridor) (14 - step) else step
            val col = 7
            return Pair((col + 0.5f) * cellSize, (row + 0.5f) * cellSize)
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
            1 -> Pair(6, 1) // Blue Start
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
            12 -> Pair(0, 7) // Yellow Pre-Home / Arrow ↓
            13 -> Pair(0, 8)

            // Top Arm (right column of top arm, moving South towards center):
            14 -> Pair(1, 8) // Yellow Start (Safe square / star)
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
            25 -> Pair(7, 14) // Green Pre-Home / Arrow ←
            26 -> Pair(8, 14)

            // Right Arm (bottom row of right arm, moving West towards center):
            27 -> Pair(8, 13) // Green Start (Safe square / star)
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
            38 -> Pair(14, 7) // Red Pre-Home / Arrow ↑
            39 -> Pair(14, 6)

            // Bottom Arm (left column of bottom arm, moving North towards center):
            40 -> Pair(13, 6) // Red Start (Safe square / star)
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
            51 -> Pair(7, 0) // Blue Pre-Home / Arrow →

            else -> Pair(6, 0)
        }
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
            duration = 450L
            interpolator = android.view.animation.AccelerateInterpolator()
            addUpdateListener { va ->
                val f = va.animatedValue as Float
                val arc = Math.sin(f * Math.PI).toFloat() * cellSize * 1.5f
                capturedX = startCoord.first + (targetCoord.first - startCoord.first) * f
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
