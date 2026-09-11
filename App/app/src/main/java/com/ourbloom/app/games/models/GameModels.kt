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
    val wager: String = "50 Sweet Kisses 💋",
    val winnerUid: String? = null, // uid, "DRAW", or null
    val winningLine: List<Int> = emptyList(),
    val status: String = "WAITING", // WAITING, PLAYING, FINISHED
    val lastMoveTimestamp: Long = System.currentTimeMillis(),
    val moveCount: Int = 0
)

data class TruthOrDareCard(
    val id: String,
    val type: String, // "TRUTH" or "DARE"
    val category: String, // "SWEET", "DEEP", "PLAYFUL", "SPICY"
    val prompt: String
)

data class WouldYouRatherQuestion(
    val id: String,
    val optionA: String,
    val optionB: String,
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

