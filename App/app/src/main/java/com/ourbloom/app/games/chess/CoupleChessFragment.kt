package com.ourbloom.app.games.chess

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.games.models.ChessState
import kotlinx.coroutines.launch

class CoupleChessFragment : Fragment() {

    private val repository = FirestoreRepository()
    private var coupleId: String = ""
    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var partnerId: String = ""
    private var partnerName: String = "Sweetheart"

    private var isOnlineMode: Boolean = true
    private var gameState = ChessState()
    private var gameListener: ListenerRegistration? = null
    private var isSubmittingMove: Boolean = false

    // Local Pass & Play State
    private var localGameState = ChessState()

    // Views
    private lateinit var chessBoardView: ChessBoardView
    private lateinit var tvGameModeStatus: TextView
    private lateinit var tvActiveWager: TextView
    private lateinit var tvTopPlayerName: TextView
    private lateinit var tvTopCaptured: TextView
    private lateinit var tvTopStatusChip: TextView
    private lateinit var tvBottomPlayerName: TextView
    private lateinit var tvBottomCaptured: TextView
    private lateinit var tvBottomStatusChip: TextView
    private lateinit var tvTurnStatus: TextView
    private lateinit var tvTurnIcon: TextView
    private lateinit var cardTurnBanner: MaterialCardView

    private val romanticWagers = listOf(
        "Winner picks our dinner date 🍽️",
        "Winner gets breakfast in bed ☕",
        "10-minute relaxing shoulder massage 💆",
        "Winner picks the movie tonight 🎬",
        "Loser does the dishes tomorrow 🧼",
        "50 Sweet Kisses anytime, anywhere 💋",
        "Late-night ice cream run on loser 🍦",
        "Loser gives a romantic handwritten love letter 💌"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_couple_chess, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)

        lifecycleScope.launch {
            val user = repository.getCurrentUser()
            if (user != null) {
                currentUserId = user.uid
                currentUserName = user.name
                coupleId = user.coupleId ?: ""

                if (coupleId.isNotBlank()) {
                    val couple = repository.getCouple(coupleId)
                    partnerId = if (couple?.user1 == currentUserId) couple?.user2 ?: "" else couple?.user1 ?: ""
                    if (partnerId.isNotBlank()) {
                        val pUser = repository.getUser(partnerId)
                        partnerName = user.nicknameForPartner?.takeIf { it.isNotBlank() }
                            ?: pUser?.name?.ifBlank { "Sweetheart" }
                            ?: "Sweetheart"
                    }
                    isOnlineMode = true
                    updateModeUI()
                    startOnlineListener()
                } else {
                    isOnlineMode = false
                    updateModeUI()
                    resetLocalGame()
                }
            } else {
                isOnlineMode = false
                updateModeUI()
                resetLocalGame()
            }
        }
    }

    private fun initViews(view: View) {
        chessBoardView = view.findViewById(R.id.chess_board_view)
        tvGameModeStatus = view.findViewById(R.id.tv_game_mode_status)
        tvActiveWager = view.findViewById(R.id.tv_active_wager)
        tvTopPlayerName = view.findViewById(R.id.tv_top_player_name)
        tvTopCaptured = view.findViewById(R.id.tv_top_captured)
        tvTopStatusChip = view.findViewById(R.id.tv_top_status_chip)
        tvBottomPlayerName = view.findViewById(R.id.tv_bottom_player_name)
        tvBottomCaptured = view.findViewById(R.id.tv_bottom_captured)
        tvBottomStatusChip = view.findViewById(R.id.tv_bottom_status_chip)
        tvTurnStatus = view.findViewById(R.id.tv_turn_status)
        tvTurnIcon = view.findViewById(R.id.tv_turn_icon)
        cardTurnBanner = view.findViewById(R.id.card_turn_banner)

        view.findViewById<ImageButton>(R.id.btn_back_chess).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<ImageButton>(R.id.btn_switch_mode).setOnClickListener {
            toggleGameMode()
        }

        view.findViewById<ImageButton>(R.id.btn_edit_wager).setOnClickListener {
            showWagerSelectionDialog()
        }

        view.findViewById<View>(R.id.card_wager_banner).setOnClickListener {
            showWagerSelectionDialog()
        }

        view.findViewById<MaterialButton>(R.id.btn_change_wager).setOnClickListener {
            showWagerSelectionDialog()
        }

        view.findViewById<MaterialButton>(R.id.btn_reset_game).setOnClickListener {
            if (isOnlineMode) {
                startNewOnlineGame()
            } else {
                resetLocalGame()
            }
        }

        chessBoardView.onSquareClickListener = { squareIndex ->
            onSquareClicked(squareIndex)
        }
    }

    private fun updateModeUI() {
        if (isOnlineMode) {
            tvGameModeStatus.text = "Online Sync 🌐"
            tvGameModeStatus.setTextColor(0xFFE91E63.toInt())
            tvTopPlayerName.text = partnerName
            tvBottomPlayerName.text = "You ($currentUserName)"
        } else {
            tvGameModeStatus.text = "Pass & Play 📱"
            tvGameModeStatus.setTextColor(0xFF1976D2.toInt())
            tvTopPlayerName.text = "Player 2 (Black ♚)"
            tvBottomPlayerName.text = "Player 1 (White ♔)"
        }
    }

    private fun toggleGameMode() {
        if (coupleId.isBlank()) {
            Toast.makeText(requireContext(), "Pair with your partner to enable Online Sync! 🌸", Toast.LENGTH_SHORT).show()
            return
        }

        isOnlineMode = !isOnlineMode
        updateModeUI()

        if (isOnlineMode) {
            Toast.makeText(requireContext(), "Switched to Online Sync mode 🌐", Toast.LENGTH_SHORT).show()
            startOnlineListener()
        } else {
            gameListener?.remove()
            gameListener = null
            Toast.makeText(requireContext(), "Switched to Pass & Play mode 📱", Toast.LENGTH_SHORT).show()
            localGameState = gameState.copy()
            renderState(localGameState)
        }
    }

    private fun startOnlineListener() {
        if (coupleId.isBlank()) return
        gameListener?.remove()

        gameListener = repository.observeChessState(coupleId) { state ->
            if (!isAdded) return@observeChessState

            // If empty, initialize online state with White player as current user
            if (state.whitePlayerUid.isBlank() && state.blackPlayerUid.isBlank()) {
                startNewOnlineGame()
                return@observeChessState
            }

            gameState = state
            isSubmittingMove = false
            renderState(state)
        }
    }

    private fun renderState(state: ChessState) {
        chessBoardView.board = state.board
        chessBoardView.lastMoveFrom = state.lastMoveFrom
        chessBoardView.lastMoveTo = state.lastMoveTo
        tvActiveWager.text = state.wager

        // If current user is Black player, orient board so Black is on bottom
        val isCurrentUserBlack = isOnlineMode && state.blackPlayerUid == currentUserId
        chessBoardView.isFlipped = isCurrentUserBlack

        // Captured pieces
        val whiteCapturedSymbols = state.capturedByWhite.joinToString(" ") { ChessEngine.getPieceSymbol(it) }
        val blackCapturedSymbols = state.capturedByBlack.joinToString(" ") { ChessEngine.getPieceSymbol(it) }

        if (isCurrentUserBlack) {
            tvBottomCaptured.text = blackCapturedSymbols
            tvTopCaptured.text = whiteCapturedSymbols
        } else {
            tvBottomCaptured.text = whiteCapturedSymbols
            tvTopCaptured.text = blackCapturedSymbols
        }

        // Check if current turn player's King is in check
        val kingInCheck = ChessEngine.isKingInCheck(state.board, state.turnColor)
        val kingIndex = if (kingInCheck) ChessEngine.findKingIndex(state.board, state.turnColor) else -1
        chessBoardView.kingInCheckIndex = kingIndex

        // Turn & Status banners
        val isWhiteTurn = state.turnColor == "WHITE"
        val isMyTurn = if (isOnlineMode) {
            (isWhiteTurn && state.whitePlayerUid == currentUserId) ||
            (!isWhiteTurn && state.blackPlayerUid == currentUserId)
        } else {
            true // In pass & play, players share screen
        }

        if (state.status == "CHECKMATE") {
            val winnerName = if (state.winnerUid == currentUserId) "You" else partnerName
            tvTurnIcon.text = "👑"
            tvTurnStatus.text = "Checkmate! $winnerName won the duel! 🏆"
            cardTurnBanner.strokeColor = 0xFFFFD54F.toInt()
        } else if (state.status == "STALEMATE") {
            tvTurnIcon.text = "🤝"
            tvTurnStatus.text = "Stalemate! The match is a draw."
            cardTurnBanner.strokeColor = 0xFFB0BEC5.toInt()
        } else if (kingInCheck) {
            tvTurnIcon.text = "⚠️"
            val targetColorName = if (isWhiteTurn) "White" else "Black"
            tvTurnStatus.text = "$targetColorName King is in CHECK! 🔥"
            cardTurnBanner.strokeColor = 0xFFEF5350.toInt()
        } else {
            tvTurnIcon.text = if (isWhiteTurn) "♔" else "♚"
            if (isOnlineMode) {
                if (isMyTurn) {
                    tvTurnStatus.text = "Your Turn (${if (isWhiteTurn) "White ♔" else "Black ♚"})"
                    cardTurnBanner.strokeColor = 0xFFC8E6C9.toInt()
                } else {
                    tvTurnStatus.text = "$partnerName's Turn (${if (isWhiteTurn) "White ♔" else "Black ♚"})"
                    cardTurnBanner.strokeColor = 0xFFE0E0E0.toInt()
                }
            } else {
                tvTurnStatus.text = "${if (isWhiteTurn) "White's Turn ♔" else "Black's Turn ♚"}"
                cardTurnBanner.strokeColor = 0xFFE0E0E0.toInt()
            }
        }

        tvBottomStatusChip.text = if (isMyTurn) "To Move 🌸" else "Waiting ⏳"
        tvTopStatusChip.text = if (!isMyTurn) "To Move 🌸" else "Waiting ⏳"
    }

    private fun onSquareClicked(index: Int) {
        triggerHaptic()
        val activeState = if (isOnlineMode) gameState else localGameState

        if (activeState.status == "CHECKMATE" || activeState.status == "STALEMATE") {
            Toast.makeText(requireContext(), "Match concluded! Tap 'New Duel' to play again 🔄", Toast.LENGTH_SHORT).show()
            return
        }

        // Online authorization check
        if (isOnlineMode) {
            if (isSubmittingMove) return
            val isMyTurn = (activeState.turnColor == "WHITE" && activeState.whitePlayerUid == currentUserId) ||
                           (activeState.turnColor == "BLACK" && activeState.blackPlayerUid == currentUserId)
            if (!isMyTurn) {
                Toast.makeText(requireContext(), "Wait for $partnerName's move ⏳", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val currentSelected = chessBoardView.selectedSquare
        val currentValid = chessBoardView.validMoves

        // 1. If tapping an already highlighted valid destination square -> EXECUTE MOVE
        if (currentSelected != -1 && currentValid.contains(index)) {
            executeMove(currentSelected, index)
            chessBoardView.selectedSquare = -1
            chessBoardView.validMoves = emptyList()
            return
        }

        // 2. If tapping the same square -> UNSELECT
        if (currentSelected == index) {
            chessBoardView.selectedSquare = -1
            chessBoardView.validMoves = emptyList()
            return
        }

        // 3. If tapping a piece of the current turn player's color -> SELECT & HIGHLIGHT
        val piece = activeState.board.getOrElse(index) { "" }
        if (piece.isNotBlank() && ChessEngine.getPieceColor(piece) == activeState.turnColor) {
            val legalMoves = ChessEngine.getLegalMoves(activeState.board, index, activeState.turnColor)
            if (legalMoves.isEmpty()) {
                Toast.makeText(requireContext(), "No legal moves for this piece", Toast.LENGTH_SHORT).show()
            }
            chessBoardView.selectedSquare = index
            chessBoardView.validMoves = legalMoves
        } else {
            chessBoardView.selectedSquare = -1
            chessBoardView.validMoves = emptyList()
        }
    }

    private fun executeMove(from: Int, to: Int) {
        val currentState = if (isOnlineMode) gameState else localGameState
        val capturedPiece = currentState.board.getOrElse(to) { "" }
        val movingPiece = currentState.board.getOrElse(from) { "" }
        val simulatedBoard = ChessEngine.simulateMove(currentState.board, from, to)

        val nextTurnColor = if (currentState.turnColor == "WHITE") "BLACK" else "WHITE"
        val nextTurnUid = if (isOnlineMode) {
            if (nextTurnColor == "WHITE") currentState.whitePlayerUid else currentState.blackPlayerUid
        } else ""

        // Check if captured piece
        val updatedCapturedWhite = currentState.capturedByWhite.toMutableList()
        val updatedCapturedBlack = currentState.capturedByBlack.toMutableList()
        if (capturedPiece.isNotBlank()) {
            if (currentState.turnColor == "WHITE") {
                updatedCapturedWhite.add(capturedPiece)
            } else {
                updatedCapturedBlack.add(capturedPiece)
            }
        }

        // Check game termination conditions
        val isKingInCheck = ChessEngine.isKingInCheck(simulatedBoard, nextTurnColor)
        val hasLegalMoves = ChessEngine.hasAnyLegalMoves(simulatedBoard, nextTurnColor)

        var newStatus = "PLAYING"
        var winnerUid: String? = null
        if (!hasLegalMoves) {
            if (isKingInCheck) {
                newStatus = "CHECKMATE"
                winnerUid = if (isOnlineMode) currentUserId else currentState.turnColor
            } else {
                newStatus = "STALEMATE"
                winnerUid = "DRAW"
            }
        } else if (isKingInCheck) {
            newStatus = "CHECK"
        }

        val updatedState = currentState.copy(
            board = simulatedBoard,
            turnColor = nextTurnColor,
            turnUid = nextTurnUid,
            lastMoveFrom = from,
            lastMoveTo = to,
            lastMovePiece = movingPiece,
            capturedByWhite = updatedCapturedWhite,
            capturedByBlack = updatedCapturedBlack,
            status = newStatus,
            winnerUid = winnerUid,
            moveCount = currentState.moveCount + 1,
            lastMoveTimestamp = System.currentTimeMillis()
        )

        if (isOnlineMode) {
            isSubmittingMove = true
            gameState = updatedState
            renderState(updatedState)

            lifecycleScope.launch {
                val success = repository.updateChessState(coupleId, updatedState)
                if (!success) {
                    isSubmittingMove = false
                    Toast.makeText(requireContext(), "Failed to sync move with partner. Check connection.", Toast.LENGTH_SHORT).show()
                }
                if (newStatus == "CHECKMATE") {
                    triggerVictoryHaptic()
                    showVictoryDialog("You", updatedState.wager)
                }
            }
        } else {
            localGameState = updatedState
            renderState(updatedState)
            if (newStatus == "CHECKMATE") {
                triggerVictoryHaptic()
                showVictoryDialog(if (currentState.turnColor == "WHITE") "Player 1 (White)" else "Player 2 (Black)", updatedState.wager)
            }
        }
    }

    private fun startNewOnlineGame() {
        if (coupleId.isBlank()) return
        isSubmittingMove = false

        // Alternate starting player (White)
        val nextWhiteUid = if (gameState.whitePlayerUid == currentUserId) partnerId.ifBlank { currentUserId } else currentUserId
        val nextWhiteName = if (nextWhiteUid == currentUserId) currentUserName else partnerName
        val nextBlackUid = if (nextWhiteUid == currentUserId) partnerId.ifBlank { currentUserId } else currentUserId
        val nextBlackName = if (nextWhiteUid == currentUserId) partnerName else currentUserName

        lifecycleScope.launch {
            repository.resetChessGame(
                coupleId = coupleId,
                whitePlayerUid = nextWhiteUid,
                whitePlayerName = nextWhiteName,
                blackPlayerUid = nextBlackUid,
                blackPlayerName = nextBlackName,
                wager = gameState.wager.ifBlank { "Winner picks our dinner date 🍽️" }
            )
            Toast.makeText(requireContext(), "New Chess Duel Started! ♟️", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetLocalGame() {
        localGameState = ChessState(
            id = "chess",
            board = ChessState.defaultChessBoard(),
            whitePlayerName = "Player 1 (White)",
            blackPlayerName = "Player 2 (Black)",
            turnColor = "WHITE",
            wager = "Winner gets breakfast in bed ☕",
            status = "PLAYING"
        )
        chessBoardView.selectedSquare = -1
        chessBoardView.validMoves = emptyList()
        renderState(localGameState)
        Toast.makeText(requireContext(), "Pass & Play game reset! White to move ♔", Toast.LENGTH_SHORT).show()
    }

    private fun showWagerSelectionDialog() {
        val options = romanticWagers.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choose Romantic Wager 🎁")
            .setItems(options) { _, which ->
                val selected = options[which]
                updateWager(selected)
            }
            .setNeutralButton("Custom Bet") { _, _ ->
                showCustomWagerDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomWagerDialog() {
        val input = EditText(requireContext()).apply {
            hint = "e.g. Winner gets 100 kisses 💋"
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create Custom Wager 💌")
            .setView(input)
            .setPositiveButton("Set Wager") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) {
                    updateWager(text)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateWager(newWager: String) {
        tvActiveWager.text = newWager
        if (isOnlineMode && coupleId.isNotBlank()) {
            val updated = gameState.copy(wager = newWager)
            gameState = updated
            lifecycleScope.launch {
                repository.updateChessState(coupleId, updated)
            }
        } else {
            localGameState = localGameState.copy(wager = newWager)
        }
    }

    private fun showVictoryDialog(winner: String, wager: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("👑 Royal Victory!")
            .setMessage("Checkmate! $winner claimed the throne! 🌸\n\nReward: $wager")
            .setPositiveButton("Play Again 🔄") { _, _ ->
                if (isOnlineMode) startNewOnlineGame() else resetLocalGame()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun triggerHaptic() {
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } catch (_: Exception) {}
    }

    private fun triggerVictoryHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 150, 80, 250), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 80, 250), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(250)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        gameListener?.remove()
        gameListener = null
    }
}
