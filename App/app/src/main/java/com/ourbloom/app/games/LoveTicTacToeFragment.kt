package com.ourbloom.app.games

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.AlertDialog
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
import android.view.animation.OvershootInterpolator
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
import com.ourbloom.app.games.models.TicTacToeState
import kotlinx.coroutines.launch

class LoveTicTacToeFragment : Fragment() {

    private val repository = FirestoreRepository()
    private var coupleId: String = ""
    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var partnerId: String = ""
    private var partnerName: String = "Sweetheart"

    private var isOnlineMode: Boolean = true
    private var gameState = TicTacToeState()
    private var gameListener: ListenerRegistration? = null

    // Local Pass & Play State
    private var localTurnSymbol = "♡"
    private var localBoard = MutableList(9) { "" }
    private var localGameOver = false

    private val cells = ArrayList<TextView>(9)
    private lateinit var tvGameModeStatus: TextView
    private lateinit var tvActiveWager: TextView
    private lateinit var tvTurnSymbol: TextView
    private lateinit var tvTurnStatus: TextView
    private lateinit var cardTurnBanner: MaterialCardView

    private val winningCombinations = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // Rows
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // Cols
        listOf(0, 4, 8), listOf(2, 4, 6)             // Diagonals
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_love_tictactoe, container, false)
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
                        partnerName = pUser?.name?.ifBlank { "Sweetheart" } ?: "Sweetheart"
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
        tvGameModeStatus = view.findViewById(R.id.tv_game_mode_status)
        tvActiveWager = view.findViewById(R.id.tv_active_wager)
        tvTurnSymbol = view.findViewById(R.id.tv_turn_symbol)
        tvTurnStatus = view.findViewById(R.id.tv_turn_status)
        cardTurnBanner = view.findViewById(R.id.card_turn_banner)

        view.findViewById<ImageButton>(R.id.btn_back_tictactoe).setOnClickListener {
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

        view.findViewById<TextView>(R.id.tv_change_wager_btn).setOnClickListener {
            showWagerSelectionDialog()
        }

        view.findViewById<MaterialButton>(R.id.btn_reset_game).setOnClickListener {
            if (isOnlineMode) {
                startNewOnlineGame()
            } else {
                resetLocalGame()
            }
        }

        view.findViewById<MaterialButton>(R.id.btn_change_wager_bottom).setOnClickListener {
            showWagerSelectionDialog()
        }

        val cellIds = listOf(
            R.id.cell_0, R.id.cell_1, R.id.cell_2,
            R.id.cell_3, R.id.cell_4, R.id.cell_5,
            R.id.cell_6, R.id.cell_7, R.id.cell_8
        )

        cells.clear()
        for (i in cellIds.indices) {
            val tv = view.findViewById<TextView>(cellIds[i])
            cells.add(tv)
            tv.setOnClickListener {
                onCellClicked(i)
            }
        }
    }

    private fun updateModeUI() {
        if (isOnlineMode) {
            tvGameModeStatus.text = "Online with $partnerName 💖"
            tvGameModeStatus.setTextColor(0xFFE85D75.toInt())
        } else {
            tvGameModeStatus.text = "Pass & Play (Same Device) 📱"
            tvGameModeStatus.setTextColor(0xFF1976D2.toInt())
        }
    }

    private fun toggleGameMode() {
        if (coupleId.isBlank()) {
            Toast.makeText(requireContext(), "Connect partner profile for online multiplayer", Toast.LENGTH_SHORT).show()
            return
        }
        isOnlineMode = !isOnlineMode
        updateModeUI()
        if (isOnlineMode) {
            startOnlineListener()
            Toast.makeText(requireContext(), "Switched to Online Multiplayer with $partnerName 💖", Toast.LENGTH_SHORT).show()
        } else {
            gameListener?.remove()
            resetLocalGame()
            Toast.makeText(requireContext(), "Switched to Pass & Play on this phone 📱", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // ONLINE MULTIPLAYER (FIRESTORE)
    // ==========================================

    private fun startOnlineListener() {
        if (coupleId.isBlank()) return
        gameListener?.remove()
        gameListener = repository.observeTicTacToeState(coupleId) { state ->
            if (!isAdded) return@observeTicTacToeState
            gameState = state
            renderOnlineState(state)
        }
    }

    private fun renderOnlineState(state: TicTacToeState) {
        tvActiveWager.text = state.wager.ifBlank { "50 Sweet Kisses 💋" }

        // Render board cells
        for (i in 0 until 9) {
            val symbol = state.board.getOrElse(i) { "" }
            val cellView = cells[i]
            val oldText = cellView.text.toString()
            cellView.text = symbol

            if (symbol == "♡") {
                cellView.setTextColor(0xFFE85D75.toInt()) // Rose
            } else if (symbol == "✕") {
                cellView.setTextColor(0xFF7B1FA2.toInt()) // Violet
            }

            // Animate newly placed cell
            if (oldText.isBlank() && symbol.isNotBlank()) {
                animateCellPlacement(cellView)
            }

            // Highlight winning line
            if (state.winningLine.contains(i)) {
                cellView.setBackgroundResource(R.drawable.bg_tictactoe_cell_win)
            } else {
                cellView.setBackgroundResource(R.drawable.bg_tictactoe_cell)
            }
        }

        // Render turn / winner status
        if (state.status == "FINISHED") {
            if (state.winnerUid == "DRAW") {
                tvTurnSymbol.text = "🤝"
                tvTurnStatus.text = "It's a Sweet Draw! Play Again ♡"
                cardTurnBanner.strokeColor = 0xFFFFD54F.toInt()
            } else {
                val isMeWinner = state.winnerUid == currentUserId
                val winnerName = if (isMeWinner) "You" else partnerName
                tvTurnSymbol.text = "🏆"
                tvTurnStatus.text = "$winnerName Won the ${state.wager}! 🎉"
                cardTurnBanner.strokeColor = 0xFF4CAF50.toInt()
            }
        } else {
            val isMyTurn = state.turnUid == currentUserId || state.turnUid.isBlank()
            val mySymbol = if (state.playerXUid == currentUserId) "Hearts ♡" else "Kisses ✕"
            val partnerSymbol = if (state.playerXUid == currentUserId) "Kisses ✕" else "Hearts ♡"

            if (isMyTurn) {
                tvTurnSymbol.text = if (state.playerXUid == currentUserId) "♡" else "✕"
                tvTurnStatus.text = "Your Turn ($mySymbol)"
                cardTurnBanner.strokeColor = 0xFFFFCDD2.toInt()
            } else {
                tvTurnSymbol.text = if (state.playerXUid == currentUserId) "✕" else "♡"
                tvTurnStatus.text = "$partnerName's Turn ($partnerSymbol)"
                cardTurnBanner.strokeColor = 0xFFE0E0E0.toInt()
            }
        }
    }

    private fun onCellClicked(index: Int) {
        triggerHaptic()

        if (isOnlineMode) {
            handleOnlineCellClick(index)
        } else {
            handleLocalCellClick(index)
        }
    }

    private fun handleOnlineCellClick(index: Int) {
        if (gameState.status == "FINISHED") {
            Toast.makeText(requireContext(), "Round complete! Tap 'New Round' to play again 🔄", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if current user is authorized for this turn
        val isMyTurn = gameState.turnUid == currentUserId || gameState.turnUid.isBlank()
        if (!isMyTurn) {
            Toast.makeText(requireContext(), "Wait for $partnerName's move ⏳", Toast.LENGTH_SHORT).show()
            return
        }

        val currentVal = gameState.board.getOrElse(index) { "" }
        if (currentVal.isNotBlank()) {
            Toast.makeText(requireContext(), "Cell already taken!", Toast.LENGTH_SHORT).show()
            return
        }

        val mySymbol = if (gameState.playerXUid == currentUserId || gameState.playerXUid.isBlank()) "♡" else "✕"
        val newBoard = gameState.board.toMutableList()
        newBoard[index] = mySymbol

        // Check win
        val winCombo = checkWinningCombination(newBoard, mySymbol)
        val isWin = winCombo != null
        val isDraw = !isWin && newBoard.all { it.isNotBlank() }

        val newWinnerUid = when {
            isWin -> currentUserId
            isDraw -> "DRAW"
            else -> null
        }

        val nextTurnUid = if (isWin || isDraw) "" else partnerId
        val nextStatus = if (isWin || isDraw) "FINISHED" else "PLAYING"

        val updatedState = gameState.copy(
            board = newBoard,
            playerXUid = if (gameState.playerXUid.isBlank()) currentUserId else gameState.playerXUid,
            playerXName = if (gameState.playerXName.isBlank()) currentUserName else gameState.playerXName,
            playerOUid = if (gameState.playerOUid.isBlank()) partnerId else gameState.playerOUid,
            playerOName = if (gameState.playerOName.isBlank()) partnerName else gameState.playerOName,
            turnUid = nextTurnUid,
            winnerUid = newWinnerUid,
            winningLine = winCombo ?: emptyList(),
            status = nextStatus,
            moveCount = gameState.moveCount + 1,
            lastMoveTimestamp = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            repository.updateTicTacToeState(coupleId, updatedState)
            if (isWin) {
                triggerVictoryHaptic()
                showVictoryDialog("You", gameState.wager)
            }
        }
    }

    private fun startNewOnlineGame() {
        lifecycleScope.launch {
            repository.resetTicTacToeGame(
                coupleId = coupleId,
                playerXUid = currentUserId,
                playerXName = currentUserName,
                playerOUid = partnerId,
                playerOName = partnerName,
                wager = gameState.wager.ifBlank { "50 Sweet Kisses 💋" }
            )
            Toast.makeText(requireContext(), "New round started! 🌸", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // LOCAL PASS & PLAY LOGIC
    // ==========================================

    private fun resetLocalGame() {
        localBoard = MutableList(9) { "" }
        localTurnSymbol = "♡"
        localGameOver = false

        for (i in 0 until 9) {
            cells[i].text = ""
            cells[i].setBackgroundResource(R.drawable.bg_tictactoe_cell)
        }

        tvTurnSymbol.text = "♡"
        tvTurnStatus.text = "Player 1's Turn (Hearts ♡)"
        cardTurnBanner.strokeColor = 0xFFFFCDD2.toInt()
    }

    private fun handleLocalCellClick(index: Int) {
        if (localGameOver) {
            Toast.makeText(requireContext(), "Round complete! Tap 'New Round' to replay", Toast.LENGTH_SHORT).show()
            return
        }

        if (localBoard[index].isNotBlank()) return

        localBoard[index] = localTurnSymbol
        cells[index].text = localTurnSymbol
        if (localTurnSymbol == "♡") {
            cells[index].setTextColor(0xFFE85D75.toInt())
        } else {
            cells[index].setTextColor(0xFF7B1FA2.toInt())
        }
        animateCellPlacement(cells[index])

        val winCombo = checkWinningCombination(localBoard, localTurnSymbol)
        if (winCombo != null) {
            localGameOver = true
            winCombo.forEach { idx ->
                cells[idx].setBackgroundResource(R.drawable.bg_tictactoe_cell_win)
            }
            triggerVictoryHaptic()
            val winnerText = if (localTurnSymbol == "♡") "Player 1 (Hearts ♡)" else "Player 2 (Kisses ✕)"
            tvTurnSymbol.text = "🏆"
            tvTurnStatus.text = "$winnerText Won! 🎉"
            showVictoryDialog(winnerText, tvActiveWager.text.toString())
            return
        }

        if (localBoard.all { it.isNotBlank() }) {
            localGameOver = true
            tvTurnSymbol.text = "🤝"
            tvTurnStatus.text = "It's a Sweet Draw! 🌸"
            return
        }

        // Switch turn
        localTurnSymbol = if (localTurnSymbol == "♡") "✕" else "♡"
        tvTurnSymbol.text = localTurnSymbol
        val playerLabel = if (localTurnSymbol == "♡") "Player 1 (Hearts ♡)" else "Player 2 (Kisses ✕)"
        tvTurnStatus.text = "$playerLabel's Turn"
        cardTurnBanner.strokeColor = if (localTurnSymbol == "♡") 0xFFFFCDD2.toInt() else 0xFFE1BEE7.toInt()
    }

    private fun checkWinningCombination(board: List<String>, symbol: String): List<Int>? {
        for (combo in winningCombinations) {
            if (board[combo[0]] == symbol && board[combo[1]] == symbol && board[combo[2]] == symbol) {
                return combo
            }
        }
        return null
    }

    // ==========================================
    // ROMANTIC WAGERS & DIALOGS
    // ==========================================

    private fun showWagerSelectionDialog() {
        val wagers = arrayOf(
            "💆 10-Minute Romantic Full Body Massage",
            "🔥 Seductive lap dance or sensual massage",
            "💋 50 Sweet Kisses on demand",
            "🤫 Winner controls the bedroom tonight",
            "🍕 Winner picks dinner & dessert tonight",
            "🍳 Breakfast in bed tomorrow morning",
            "🍦 Loser treats winner to favorite ice cream",
            "✍️ Custom Couple Dare..."
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Winner's Romantic Stake 🏆")
            .setItems(wagers) { _, which ->
                if (which == wagers.size - 1) {
                    showCustomWagerInput()
                } else {
                    applyWager(wagers[which])
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomWagerInput() {
        val input = EditText(requireContext())
        input.hint = "e.g. Winner gets foot massage"
        input.setSingleLine()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Custom Romantic Stake 💖")
            .setView(input)
            .setPositiveButton("Set Stake") { _, _ ->
                val custom = input.text.toString().trim()
                if (custom.isNotBlank()) {
                    applyWager("💖 $custom")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyWager(wagerText: String) {
        tvActiveWager.text = wagerText
        if (isOnlineMode && coupleId.isNotBlank()) {
            val updated = gameState.copy(wager = wagerText)
            lifecycleScope.launch {
                repository.updateTicTacToeState(coupleId, updated)
            }
        }
        Toast.makeText(requireContext(), "Reward updated: $wagerText", Toast.LENGTH_SHORT).show()
    }

    private fun showVictoryDialog(winnerName: String, wager: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🏆 $winnerName Won!")
            .setMessage("Congratulations! The winner is entitled to:\n\n✨ $wager ✨\n\nClaim your reward from your sweetheart today! ♡")
            .setPositiveButton("Rematch 🔄") { _, _ ->
                if (isOnlineMode) {
                    startNewOnlineGame()
                } else {
                    resetLocalGame()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun animateCellPlacement(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0f, 1.25f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0f, 1.25f, 1.0f)
        val set = AnimatorSet()
        set.playTogether(scaleX, scaleY)
        set.duration = 300
        set.interpolator = OvershootInterpolator()
        set.start()
    }

    private fun triggerHaptic() {
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}
    }

    private fun triggerVictoryHaptic() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 150, 100, 200, 100, 300)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(400)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        gameListener?.remove()
    }
}
