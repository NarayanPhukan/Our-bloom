package com.ourbloom.app.games.ludo

object LudoLogic {

    const val TRACK_SIZE = 52
    const val HOME_STEPS = 5
    const val HOME_POS = 99
    const val YARD_POS = -1

    // Player 1 (Rose Hearts): Starts at 1, runs to 51, enters 101..105, then 99
    const val P1_START = 1
    const val P1_PRE_HOME = 51
    const val P1_HOME_CORRIDOR_BASE = 100 // 101..105

    // Player 2 (Sky Stars): Starts at 27, runs 27..51, then 0..25, enters 201..205, then 99
    const val P2_START = 27
    const val P2_PRE_HOME = 25
    const val P2_HOME_CORRIDOR_BASE = 200 // 201..205

    // Safe Squares on standard Ludo track (where tokens cannot be captured)
    val SAFE_SQUARES = setOf(1, 9, 14, 22, 27, 35, 40, 48)

    fun isSafeSquare(pos: Int): Boolean = SAFE_SQUARES.contains(pos) || pos > 100

    /**
     * Determines if a token at [currentPos] can legally move with [dice].
     */
    fun canTokenMove(currentPos: Int, dice: Int, player: Int): Boolean {
        if (currentPos == HOME_POS) return false

        // In Yard: only roll of 6 can deploy token
        if (currentPos == YARD_POS) {
            return dice == 6
        }

        // In Home Corridor: requires exact steps to enter Home (99)
        if (currentPos > 100) {
            val stepInCorridor = currentPos % 100 // 1..5
            val remainingToHome = (HOME_STEPS + 1) - stepInCorridor
            return dice <= remainingToHome
        }

        // On Main Track:
        val relativeDist = getRelativeDistanceTraveled(currentPos, player)
        val newDist = relativeDist + dice

        // If move stays on track (up to 50 steps)
        if (newDist <= 50) return true

        // If move enters home corridor (51..55) or exactly Home (56)
        if (newDist <= 56) return true

        // Overshoots home
        return false
    }

    /**
     * Calculates new position after moving [dice] steps.
     */
    fun calculateNewPosition(currentPos: Int, dice: Int, player: Int): Int {
        if (currentPos == YARD_POS) {
            return if (player == 1) P1_START else P2_START
        }

        // In Home corridor
        if (currentPos > 100) {
            val base = if (player == 1) P1_HOME_CORRIDOR_BASE else P2_HOME_CORRIDOR_BASE
            val currentStep = currentPos % 100
            val targetStep = currentStep + dice
            return if (targetStep > HOME_STEPS) HOME_POS else base + targetStep
        }

        // On main track
        val relativeDist = getRelativeDistanceTraveled(currentPos, player)
        val newDist = relativeDist + dice

        if (newDist <= 50) {
            // Still on main track
            val start = if (player == 1) P1_START else P2_START
            return (start + newDist) % TRACK_SIZE
        } else {
            // Entering home corridor or reaching home
            val corridorStep = newDist - 50 // 1..6
            if (corridorStep > HOME_STEPS) {
                return HOME_POS
            } else {
                val base = if (player == 1) P1_HOME_CORRIDOR_BASE else P2_HOME_CORRIDOR_BASE
                return base + corridorStep
            }
        }
    }

    /**
     * Number of steps moved from starting cell (0 to 50).
     */
    fun getRelativeDistanceTraveled(currentPos: Int, player: Int): Int {
        val start = if (player == 1) P1_START else P2_START
        return (currentPos - start + TRACK_SIZE) % TRACK_SIZE
    }

    /**
     * Returns list of token indices (0..3) that can legally move with [dice].
     */
    fun getMovableTokens(tokens: List<Int>, dice: Int, player: Int): List<Int> {
        val movable = mutableListOf<Int>()
        for (i in tokens.indices) {
            if (canTokenMove(tokens[i], dice, player)) {
                movable.add(i)
            }
        }
        return movable
    }

    /**
     * Checks if all 4 tokens have reached the Heartland Home (99).
     */
    fun isWin(tokens: List<Int>): Boolean {
        return tokens.size == 4 && tokens.all { it == HOME_POS }
    }
}
