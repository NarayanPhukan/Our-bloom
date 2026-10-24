package com.ourbloom.app.games.chess

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.ourbloom.app.games.models.ChessState
import kotlin.math.min

class ChessBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var board: List<String> = ChessState.defaultChessBoard()
        set(value) {
            field = value
            invalidate()
        }

    var selectedSquare: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    var validMoves: List<Int> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var lastMoveFrom: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    var lastMoveTo: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    var kingInCheckIndex: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    var isFlipped: Boolean = false // If Black player, flip board so Black is on bottom
        set(value) {
            field = value
            invalidate()
        }

    var onSquareClickListener: ((Int) -> Unit)? = null

    // Palette: Romantic Ivory & Rose Gold
    private val lightSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF5F8") // Warm light cream-pink
    }
    private val darkSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F4BCCB") // Elegant Rose Gold Petal
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFD54F") // Warm Honey Glow
    }
    private val lastMovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#60A5D6A7") // Soft Spring Mint
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99EF5350") // Warning Coral Red
    }
    private val validDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D81B60") // Love Rose
        style = Paint.Style.FILL
    }
    private val validCaptureRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E91E63")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val whitePiecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#37474F") // Slate Charcoal border/glyph
        textAlign = Paint.Align.CENTER
        textSize = 64f
    }
    private val blackPiecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#880E4F") // Deep Rose Velvet
        textAlign = Paint.Align.CENTER
        textSize = 64f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        textSize = 24f
    }

    private var squareSize = 0f
    private val squareRect = RectF()
    private val textBounds = Rect()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (width > 0 && height > 0) min(width, height) else if (width > 0) width else height
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        squareSize = size / 8f
        whitePiecePaint.textSize = squareSize * 0.72f
        blackPiecePaint.textSize = squareSize * 0.72f
        labelPaint.textSize = squareSize * 0.22f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (squareSize <= 0) return

        for (row in 0..7) {
            for (col in 0..7) {
                val displayRow = if (isFlipped) 7 - row else row
                val displayCol = if (isFlipped) 7 - col else col
                val boardIndex = row * 8 + col

                val left = displayCol * squareSize
                val top = displayRow * squareSize
                val right = left + squareSize
                val bottom = top + squareSize
                squareRect.set(left, top, right, bottom)

                // 1. Base alternating squares
                val isDark = (row + col) % 2 != 0
                canvas.drawRect(squareRect, if (isDark) darkSquarePaint else lightSquarePaint)

                // 2. Last move highlight
                if (boardIndex == lastMoveFrom || boardIndex == lastMoveTo) {
                    canvas.drawRect(squareRect, lastMovePaint)
                }

                // 3. Selected square highlight
                if (boardIndex == selectedSquare) {
                    canvas.drawRect(squareRect, selectedPaint)
                }

                // 4. King in check highlight
                if (boardIndex == kingInCheckIndex) {
                    canvas.drawRect(squareRect, checkPaint)
                }

                // 5. Piece drawing
                val piece = board.getOrElse(boardIndex) { "" }
                if (piece.isNotBlank()) {
                    val symbol = ChessEngine.getPieceSymbol(piece)
                    val paint = if (piece.startsWith("w")) whitePiecePaint else blackPiecePaint
                    val centerX = left + squareSize / 2f
                    // Center vertically on baseline
                    paint.getTextBounds(symbol, 0, symbol.length, textBounds)
                    val centerY = top + squareSize / 2f - textBounds.exactCenterY()
                    canvas.drawText(symbol, centerX, centerY, paint)
                }

                // 6. Valid destination highlights
                if (validMoves.contains(boardIndex)) {
                    val centerX = left + squareSize / 2f
                    val centerY = top + squareSize / 2f
                    if (piece.isNotBlank()) {
                        // Target has capture piece: draw outer capture ring
                        canvas.drawCircle(centerX, centerY, squareSize * 0.42f, validCaptureRingPaint)
                    } else {
                        // Empty destination: draw filled centered dot
                        canvas.drawCircle(centerX, centerY, squareSize * 0.16f, validDotPaint)
                    }
                }

                // 7. Coordinate labels (ranks on col 0, files on row 7)
                if (displayCol == 0) {
                    val rankText = (8 - row).toString()
                    canvas.drawText(rankText, left + 4f, top + labelPaint.textSize + 2f, labelPaint)
                }
                if (displayRow == 7) {
                    val fileText = ('a' + col).toString()
                    canvas.drawText(fileText, right - labelPaint.textSize - 2f, bottom - 4f, labelPaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val col = (event.x / squareSize).toInt().coerceIn(0, 7)
            val row = (event.y / squareSize).toInt().coerceIn(0, 7)

            val actualRow = if (isFlipped) 7 - row else row
            val actualCol = if (isFlipped) 7 - col else col
            val index = actualRow * 8 + actualCol

            onSquareClickListener?.invoke(index)
            return true
        }
        return true
    }
}
