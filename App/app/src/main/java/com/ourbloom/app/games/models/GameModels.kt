package com.ourbloom.app.games.models
 
import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties
 
@Keep
@IgnoreExtraProperties
data class TicTacToeState(
    val id: String = "tictactoe",
    val board: List<String> = List(9) { "" },
    val playerXUid: String = "",
    val playerXName: String = "",
    val playerOUid: String = "",
    val playerOName: String = "",
    val turnUid: String = "",
    val lastMovePlayerUid: String = "",
    val wager: String = "50 Sweet Kisses 💋",
    val winnerUid: String? = null, // uid, "DRAW", or null
    val winningLine: List<Int> = emptyList(),
    val status: String = "WAITING", // WAITING, PLAYING, FINISHED
    val lastMoveTimestamp: Long = System.currentTimeMillis(),
    val moveCount: Int = 0
)

@Keep
@IgnoreExtraProperties
data class TruthOrDareCard(
    val id: String = "",
    val type: String = "TRUTH", // "TRUTH" or "DARE"
    val category: String = "SWEET", // "SWEET", "DEEP", "PLAYFUL", "SPICY", "ENDLESS", "CUSTOM"
    val prompt: String = ""
)

@Keep
@IgnoreExtraProperties
data class CustomTruthOrDareCard(
    val id: String = "",
    val type: String = "TRUTH", // "TRUTH" or "DARE"
    val category: String = "SPICY",
    val prompt: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class TruthOrDareSyncState(
    val id: String = "truthordare",
    val currentCardId: String = "",
    val currentType: String = "TRUTH", // "TRUTH" or "DARE"
    val currentCategory: String = "SWEET",
    val currentPrompt: String = "",
    val drawnByUid: String = "",
    val drawnByName: String = "",
    val targetUid: String = "",
    val targetName: String = "",
    val status: String = "IDLE", // IDLE, ACTIVE, COMPLETED
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val turnCount: Int = 0
)

@Keep
@IgnoreExtraProperties
data class WouldYouRatherQuestion(
    val id: String = "",
    val optionA: String = "",
    val optionB: String = "",
    val category: String = "Romantic"
)

@Keep
@IgnoreExtraProperties
data class WouldYouRatherSyncState(
    val id: String = "wouldyourather",
    val questionIndex: Int = 0,
    val player1Uid: String = "",
    val player1Choice: String = "",
    val player2Uid: String = "",
    val player2Choice: String = "",
    val isRevealed: Boolean = false,
    val matchCount: Int = 0,
    val totalAnswered: Int = 0,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class ChessState(
    val id: String = "chess",
    // 64 squares: empty string "" or piece string ("wP", "wN", "wB", "wR", "wQ", "wK", "bP", "bN", "bB", "bR", "bQ", "bK")
    val board: List<String> = defaultChessBoard(),
    val whitePlayerUid: String = "",
    val whitePlayerName: String = "",
    val blackPlayerUid: String = "",
    val blackPlayerName: String = "",
    val turnUid: String = "",
    val turnColor: String = "WHITE", // "WHITE" or "BLACK"
    val wager: String = "Winner picks our dinner date 🍽️",
    val status: String = "PLAYING", // "PLAYING", "CHECK", "CHECKMATE", "STALEMATE", "RESIGNED"
    val winnerUid: String? = null,
    val lastMoveFrom: Int = -1,
    val lastMoveTo: Int = -1,
    val lastMovePiece: String = "",
    val capturedByWhite: List<String> = emptyList(),
    val capturedByBlack: List<String> = emptyList(),
    val moveCount: Int = 0,
    val lastMoveTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun defaultChessBoard(): List<String> {
            return listOf(
                "bR", "bN", "bB", "bQ", "bK", "bB", "bN", "bR", // rank 8 (0..7)
                "bP", "bP", "bP", "bP", "bP", "bP", "bP", "bP", // rank 7 (8..15)
                "", "", "", "", "", "", "", "",                 // rank 6 (16..23)
                "", "", "", "", "", "", "", "",                 // rank 5 (24..31)
                "", "", "", "", "", "", "", "",                 // rank 4 (32..39)
                "", "", "", "", "", "", "", "",                 // rank 3 (40..47)
                "wP", "wP", "wP", "wP", "wP", "wP", "wP", "wP", // rank 2 (48..55)
                "wR", "wN", "wB", "wQ", "wK", "wB", "wN", "wR"  // rank 1 (56..63)
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class LudoState(
    val id: String = "ludo",
    val player1Uid: String = "", // Player 1 (Rose Hearts)
    val player1Name: String = "",
    val player2Uid: String = "", // Player 2 (Royal Sky Stars)
    val player2Name: String = "",
    // Positions: -1 = yard/base, 0..27 = track, 28..32 = home corridor, 99 = in home castle
    val player1Tokens: List<Int> = listOf(-1, -1, -1, -1),
    val player2Tokens: List<Int> = listOf(-1, -1, -1, -1),
    val turnPlayer: Int = 1, // 1 or 2
    val turnUid: String = "",
    val currentDiceValue: Int = 0, // 0 = not rolled, 1..6 = rolled
    val hasRolledDice: Boolean = false,
    val status: String = "PLAYING", // "PLAYING", "FINISHED"
    val winnerUid: String? = null,
    val wager: String = "Winner gets breakfast in bed ☕",
    val lastActionMessage: String = "Roll the dice to start the duel! 🎲",
    val consecutiveSixes: Int = 0,
    val moveCount: Int = 0,
    val lastMoveTimestamp: Long = System.currentTimeMillis()
)

