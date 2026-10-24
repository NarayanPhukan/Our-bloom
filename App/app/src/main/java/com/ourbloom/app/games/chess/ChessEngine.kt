package com.ourbloom.app.games.chess

object ChessEngine {

    fun getPieceColor(piece: String): String? {
        return when {
            piece.startsWith("w") -> "WHITE"
            piece.startsWith("b") -> "BLACK"
            else -> null
        }
    }

    fun getPieceType(piece: String): String {
        return if (piece.length >= 2) piece.substring(1) else ""
    }

    fun getPieceSymbol(piece: String): String {
        return when (piece) {
            "wK" -> "♔"
            "wQ" -> "♕"
            "wR" -> "♖"
            "wB" -> "♗"
            "wN" -> "♘"
            "wP" -> "♙"
            "bK" -> "♚"
            "bQ" -> "♛"
            "bR" -> "♜"
            "bB" -> "♝"
            "bN" -> "♞"
            "bP" -> "♟"
            else -> ""
        }
    }

    fun getPieceTitle(piece: String): String {
        return when (piece) {
            "wK", "bK" -> "King"
            "wQ", "bQ" -> "Queen"
            "wR", "bR" -> "Rook"
            "wB", "bB" -> "Bishop"
            "wN", "bN" -> "Knight"
            "wP", "bP" -> "Pawn"
            else -> ""
        }
    }

    /**
     * Returns strictly legal destination indices for the piece at [fromIndex].
     * Filters out any moves that leave the current player's King in check.
     */
    fun getLegalMoves(board: List<String>, fromIndex: Int, turnColor: String): List<Int> {
        val piece = board.getOrElse(fromIndex) { "" }
        if (piece.isBlank() || getPieceColor(piece) != turnColor) return emptyList()

        val pseudoMoves = getPseudoLegalMoves(board, fromIndex)
        return pseudoMoves.filter { toIndex ->
            val simulatedBoard = simulateMove(board, fromIndex, toIndex)
            !isKingInCheck(simulatedBoard, turnColor)
        }
    }

    /**
     * Checks if all legal moves for [turnColor] are exhausted.
     */
    fun hasAnyLegalMoves(board: List<String>, turnColor: String): Boolean {
        for (i in 0 until 64) {
            val piece = board[i]
            if (getPieceColor(piece) == turnColor) {
                if (getLegalMoves(board, i, turnColor).isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Returns true if the King of [kingColor] is under direct attack.
     */
    fun isKingInCheck(board: List<String>, kingColor: String): Boolean {
        val targetKing = if (kingColor == "WHITE") "wK" else "bK"
        val kingIndex = board.indexOf(targetKing)
        if (kingIndex == -1) return false // King captured or missing

        val opponentColor = if (kingColor == "WHITE") "BLACK" else "WHITE"
        for (i in 0 until 64) {
            val piece = board[i]
            if (getPieceColor(piece) == opponentColor) {
                val moves = getPseudoLegalMoves(board, i, checkKingAttackOnly = true)
                if (moves.contains(kingIndex)) {
                    return true
                }
            }
        }
        return false
    }

    fun findKingIndex(board: List<String>, kingColor: String): Int {
        val target = if (kingColor == "WHITE") "wK" else "bK"
        return board.indexOf(target)
    }

    /**
     * Generates moves without checking if own King is left in check.
     */
    private fun getPseudoLegalMoves(
        board: List<String>,
        from: Int,
        checkKingAttackOnly: Boolean = false
    ): List<Int> {
        val piece = board.getOrElse(from) { "" }
        if (piece.isBlank()) return emptyList()

        val color = getPieceColor(piece) ?: return emptyList()
        val type = getPieceType(piece)
        val r = from / 8
        val c = from % 8
        val moves = mutableListOf<Int>()

        when (type) {
            "P" -> {
                val dir = if (color == "WHITE") -1 else 1
                val startRow = if (color == "WHITE") 6 else 1

                if (!checkKingAttackOnly) {
                    // Forward 1 step
                    val f1 = (r + dir) * 8 + c
                    if (isValidPos(r + dir, c) && board[f1].isBlank()) {
                        moves.add(f1)
                        // Forward 2 steps from starting rank
                        val f2 = (r + dir * 2) * 8 + c
                        if (r == startRow && isValidPos(r + dir * 2, c) && board[f2].isBlank()) {
                            moves.add(f2)
                        }
                    }
                }

                // Diagonal captures (or attacks on King)
                val diagCols = listOf(c - 1, c + 1)
                for (dc in diagCols) {
                    if (isValidPos(r + dir, dc)) {
                        val diagIdx = (r + dir) * 8 + dc
                        val target = board[diagIdx]
                        if (checkKingAttackOnly) {
                            moves.add(diagIdx)
                        } else if (target.isNotBlank() && getPieceColor(target) != color) {
                            moves.add(diagIdx)
                        }
                    }
                }
            }

            "N" -> {
                val offsets = listOf(
                    Pair(-2, -1), Pair(-2, 1), Pair(-1, -2), Pair(-1, 2),
                    Pair(1, -2), Pair(1, 2), Pair(2, -1), Pair(2, 1)
                )
                for ((dr, dc) in offsets) {
                    val nr = r + dr
                    val nc = c + dc
                    if (isValidPos(nr, nc)) {
                        val idx = nr * 8 + nc
                        val target = board[idx]
                        if (target.isBlank() || getPieceColor(target) != color) {
                            moves.add(idx)
                        }
                    }
                }
            }

            "B" -> {
                addRayMoves(board, r, c, listOf(Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1)), color, moves)
            }

            "R" -> {
                addRayMoves(board, r, c, listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)), color, moves)
            }

            "Q" -> {
                addRayMoves(
                    board, r, c,
                    listOf(
                        Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1),
                        Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
                    ),
                    color, moves
                )
            }

            "K" -> {
                val kingOffsets = listOf(
                    Pair(-1, -1), Pair(-1, 0), Pair(-1, 1),
                    Pair(0, -1), Pair(0, 1),
                    Pair(1, -1), Pair(1, 0), Pair(1, 1)
                )
                for ((dr, dc) in kingOffsets) {
                    val nr = r + dr
                    val nc = c + dc
                    if (isValidPos(nr, nc)) {
                        val idx = nr * 8 + nc
                        val target = board[idx]
                        if (target.isBlank() || getPieceColor(target) != color) {
                            moves.add(idx)
                        }
                    }
                }
            }
        }

        return moves
    }

    private fun addRayMoves(
        board: List<String>,
        r: Int,
        c: Int,
        dirs: List<Pair<Int, Int>>,
        color: String,
        outMoves: MutableList<Int>
    ) {
        for ((dr, dc) in dirs) {
            var currR = r + dr
            var currC = c + dc
            while (isValidPos(currR, currC)) {
                val idx = currR * 8 + currC
                val target = board[idx]
                if (target.isBlank()) {
                    outMoves.add(idx)
                } else {
                    if (getPieceColor(target) != color) {
                        outMoves.add(idx)
                    }
                    break
                }
                currR += dr
                currC += dc
            }
        }
    }

    private fun isValidPos(r: Int, c: Int): Boolean = r in 0..7 && c in 0..7

    /**
     * Executes a move on a temporary copy of the board, handling pawn promotion to Queen.
     */
    fun simulateMove(board: List<String>, from: Int, to: Int): List<String> {
        val newBoard = board.toMutableList()
        var piece = newBoard[from]
        newBoard[from] = ""

        // Auto-promote Pawn reaching the last rank to Queen
        if (piece == "wP" && to / 8 == 0) {
            piece = "wQ"
        } else if (piece == "bP" && to / 8 == 7) {
            piece = "bQ"
        }

        newBoard[to] = piece
        return newBoard
    }
}
