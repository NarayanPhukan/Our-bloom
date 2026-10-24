package com.ourbloom.app.games.ludo

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.ourbloom.app.games.models.LudoState
import kotlinx.coroutines.launch
import kotlin.random.Random

class CoupleLudoFragment : Fragment() {

    private val repository = FirestoreRepository()
    private var coupleId: String = ""
    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var partnerId: String = ""
    private var partnerName: String = "Sweetheart"

    private var isOnlineMode: Boolean = true
    private var gameState = LudoState()
    private var gameListener: ListenerRegistration? = null
    private var isSubmittingMove: Boolean = false
    private var isRollingDiceAnimation: Boolean = false

    // Local Pass & Play State
    private var localGameState = LudoState()

    // Views
    private lateinit var ludoBoardView: LudoBoardView
    private lateinit var tvGameModeStatus: TextView
    private lateinit var tvActiveWager: TextView
    private lateinit var tvP1Name: TextView
    private lateinit var tvP1HomeCount: TextView
    private lateinit var tvP1TurnChip: TextView
    private lateinit var tvP2Name: TextView
    private lateinit var tvP2HomeCount: TextView
    private lateinit var tvP2TurnChip: TextView
    private lateinit var btnRollDice: MaterialCardView
    private lateinit var tvDiceDisplay: TextView
    private lateinit var tvDiceHint: TextView
    private lateinit var tvLudoActionMessage: TextView

    private val mainHandler = Handler(Looper.getMainLooper())

    private val diceSymbols = listOf("⚀", "⚁", "⚂", "⚃", "⚄", "⚅")

    private val romanticWagers = listOf(
        "Winner gets breakfast in bed ☕",
        "Winner picks our dinner date 🍽️",
        "10-minute relaxing foot/shoulder massage 💆",
        "Winner chooses the movie tonight 🎬",
        "Loser does the dishes for a day 🧼",
        "100 Sweet Kisses anytime, anywhere 💋",
        "Loser buys favorite boba / dessert 🧋",
        "Loser plans the next weekend date 🌸"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_couple_ludo, container, false)
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
        ludoBoardView = view.findViewById(R.id.ludo_board_view)
        tvGameModeStatus = view.findViewById(R.id.tv_game_mode_status)
        tvActiveWager = view.findViewById(R.id.tv_active_wager)
        tvP1Name = view.findViewById(R.id.tv_p1_name)
        tvP1HomeCount = view.findViewById(R.id.tv_p1_home_count)
        tvP1TurnChip = view.findViewById(R.id.tv_p1_turn_chip)
        tvP2Name = view.findViewById(R.id.tv_p2_name)
        tvP2HomeCount = view.findViewById(R.id.tv_p2_home_count)
        tvP2TurnChip = view.findViewById(R.id.tv_p2_turn_chip)
        btnRollDice = view.findViewById(R.id.btn_roll_dice)
        tvDiceDisplay = view.findViewById(R.id.tv_dice_display)
        tvDiceHint = view.findViewById(R.id.tv_dice_hint)
        tvLudoActionMessage = view.findViewById(R.id.tv_ludo_action_message)

        view.findViewById<ImageButton>(R.id.btn_back_ludo).setOnClickListener {
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

        btnRollDice.setOnClickListener {
            onRollDiceClicked()
        }

        ludoBoardView.onTokenClickListener = { tokenIndex ->
            onTokenClicked(tokenIndex)
        }
    }

    private fun updateModeUI() {
        if (isOnlineMode) {
            tvGameModeStatus.text = "Online Sync 🌐"
            tvGameModeStatus.setTextColor(0xFFE91E63.toInt())
            tvP1Name.text = "You ($currentUserName) ♡"
            tvP2Name.text = "$partnerName ⭐️"
        } else {
            tvGameModeStatus.text = "Pass & Play 📱"
            tvGameModeStatus.setTextColor(0xFF1976D2.toInt())
            tvP1Name.text = "Player 1 (Rose Hearts ♡)"
            tvP2Name.text = "Player 2 (Sky Stars ⭐️)"
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

        gameListener = repository.observeLudoState(coupleId) { state ->
            if (!isAdded) return@observeLudoState

            // If empty or unassigned in Firestore, seed online state with current user as Player 1
            if (state.player1Uid.isBlank() && state.player2Uid.isBlank()) {
                val seedP1Uid = currentUserId
                val seedP1Name = currentUserName
                val seedP2Uid = partnerId.ifBlank { "partner" }
                val seedP2Name = partnerName

                val seeded = LudoState(
                    id = "ludo",
                    player1Uid = seedP1Uid,
                    player1Name = seedP1Name,
                    player2Uid = seedP2Uid,
                    player2Name = seedP2Name,
                    turnUid = seedP1Uid,
                    turnPlayer = 1,
                    wager = state.wager.ifBlank { "Winner gets breakfast in bed ☕" },
                    status = "PLAYING"
                )
                gameState = seeded
                isSubmittingMove = false
                renderState(seeded)

                lifecycleScope.launch {
                    repository.updateLudoState(coupleId, seeded)
                }
                return@observeLudoState
            }

            gameState = state
            isSubmittingMove = false
            renderState(state)
        }
    }

    private fun renderState(state: LudoState) {
        ludoBoardView.player1Tokens = state.player1Tokens
        ludoBoardView.player2Tokens = state.player2Tokens
        ludoBoardView.activePlayer = state.turnPlayer

        tvActiveWager.text = state.wager
        tvLudoActionMessage.text = state.lastActionMessage

        val p1Home = state.player1Tokens.count { it == LudoLogic.HOME_POS }
        val p2Home = state.player2Tokens.count { it == LudoLogic.HOME_POS }
        tvP1HomeCount.text = "$p1Home/4 in Heartland 💖"
        tvP2HomeCount.text = "$p2Home/4 in Heartland 💖"

        val isP1Turn = state.turnPlayer == 1
        val isMyTurn = if (isOnlineMode) {
            (isP1Turn && state.player1Uid == currentUserId) || (!isP1Turn && state.player2Uid == currentUserId)
        } else {
            true
        }

        tvP1TurnChip.text = if (isP1Turn) "Rolling 🎲" else "Waiting ⏳"
        tvP2TurnChip.text = if (!isP1Turn) "Rolling 🎲" else "Waiting ⏳"

        // Dice face
        if (state.currentDiceValue in 1..6) {
            tvDiceDisplay.text = diceSymbols[state.currentDiceValue - 1]
        } else {
            tvDiceDisplay.text = "🎲"
        }

        // Movable tokens highlight
        if (state.hasRolledDice && state.currentDiceValue > 0) {
            val activeTokens = if (state.turnPlayer == 1) state.player1Tokens else state.player2Tokens
            val movable = LudoLogic.getMovableTokens(activeTokens, state.currentDiceValue, state.turnPlayer)
            ludoBoardView.movableTokens = if (isMyTurn) movable else emptyList()

            if (isMyTurn) {
                if (movable.isEmpty()) {
                    tvDiceHint.text = "NO MOVES POSSIBLE"
                } else {
                    tvDiceHint.text = "PICK A TOKEN TO ADVANCE"
                }
            } else {
                tvDiceHint.text = "PARTNER'S TURN"
            }
        } else {
            ludoBoardView.movableTokens = emptyList()
            tvDiceHint.text = if (isMyTurn) "TAP DICE TO ROLL" else "WAITING FOR PARTNER"
        }
    }

    private fun onRollDiceClicked() {
        val currentState = if (isOnlineMode) gameState else localGameState

        if (currentState.status == "FINISHED") {
            Toast.makeText(requireContext(), "Duel finished! Tap 'New Duel' to restart 🔄", Toast.LENGTH_SHORT).show()
            return
        }

        if (isRollingDiceAnimation || isSubmittingMove) return

        // In online mode, verify current user is authorized to roll
        if (isOnlineMode) {
            val isMyTurn = (currentState.turnPlayer == 1 && currentState.player1Uid == currentUserId) ||
                           (currentState.turnPlayer == 2 && currentState.player2Uid == currentUserId)
            if (!isMyTurn) {
                Toast.makeText(requireContext(), "Wait for $partnerName's turn ⏳", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (currentState.hasRolledDice) {
            Toast.makeText(requireContext(), "You already rolled! Pick a token to advance 🎯", Toast.LENGTH_SHORT).show()
            return
        }

        // Animate Dice
        isRollingDiceAnimation = true
        triggerDiceHaptic()
        playDiceAnimation()

        mainHandler.postDelayed({
            isRollingDiceAnimation = false
            val rolled = Random.nextInt(1, 7)
            handleDiceRolled(rolled)
        }, 500)
    }

    private fun playDiceAnimation() {
        val rotate = ObjectAnimator.ofFloat(btnRollDice, View.ROTATION, 0f, 360f)
        val scaleX = ObjectAnimator.ofFloat(btnRollDice, View.SCALE_X, 1f, 1.25f, 1f)
        val scaleY = ObjectAnimator.ofFloat(btnRollDice, View.SCALE_Y, 1f, 1.25f, 1f)

        AnimatorSet().apply {
            playTogether(rotate, scaleX, scaleY)
            duration = 450
            interpolator = OvershootInterpolator()
            start()
        }
    }

    private fun handleDiceRolled(diceValue: Int) {
        val currentState = if (isOnlineMode) gameState else localGameState
        val activePlayer = currentState.turnPlayer
        val currentTokens = if (activePlayer == 1) currentState.player1Tokens else currentState.player2Tokens
        val movable = LudoLogic.getMovableTokens(currentTokens, diceValue, activePlayer)

        val activePlayerName = if (isOnlineMode) {
            if (activePlayer == 1) currentUserName else partnerName
        } else {
            if (activePlayer == 1) "Player 1" else "Player 2"
        }

        if (movable.isEmpty()) {
            // No moves possible -> Show message and auto-pass turn after 1.2s delay
            val updatedConsecutiveSixes = if (diceValue == 6) currentState.consecutiveSixes + 1 else 0
            val nextPlayer = 3 - activePlayer
            val nextUid = if (isOnlineMode) {
                if (nextPlayer == 1) currentState.player1Uid else currentState.player2Uid
            } else ""

            val nextState = currentState.copy(
                currentDiceValue = diceValue,
                hasRolledDice = false,
                turnPlayer = nextPlayer,
                turnUid = nextUid,
                consecutiveSixes = updatedConsecutiveSixes,
                lastActionMessage = "$activePlayerName rolled a $diceValue! No tokens can move ⏳ Passing turn...",
                moveCount = currentState.moveCount + 1,
                lastMoveTimestamp = System.currentTimeMillis()
            )

            // Render rolled dice temporarily
            renderState(currentState.copy(currentDiceValue = diceValue, hasRolledDice = true))

            mainHandler.postDelayed({
                if (!isAdded) return@postDelayed
                commitState(nextState)
            }, 1200)

        } else if (movable.size == 1 && currentTokens[movable[0]] != LudoLogic.YARD_POS) {
            // Exactly 1 movable active token -> Automatically advance it
            val updatedState = currentState.copy(
                currentDiceValue = diceValue,
                hasRolledDice = true,
                lastActionMessage = "$activePlayerName rolled a $diceValue! 🎲"
            )
            renderState(updatedState)
            mainHandler.postDelayed({
                if (!isAdded) return@postDelayed
                advanceToken(movable[0], diceValue)
            }, 400)

        } else {
            // Multiple choices or deploying from yard -> Wait for player token tap
            val updatedState = currentState.copy(
                currentDiceValue = diceValue,
                hasRolledDice = true,
                lastActionMessage = "$activePlayerName rolled a $diceValue! Pick a token to advance 🎯"
            )
            commitState(updatedState)
        }
    }

    private fun onTokenClicked(tokenIndex: Int) {
        val currentState = if (isOnlineMode) gameState else localGameState
        if (!currentState.hasRolledDice || currentState.currentDiceValue == 0) {
            Toast.makeText(requireContext(), "Roll the dice first! 🎲", Toast.LENGTH_SHORT).show()
            return
        }

        val activeTokens = if (currentState.turnPlayer == 1) currentState.player1Tokens else currentState.player2Tokens
        val movable = LudoLogic.getMovableTokens(activeTokens, currentState.currentDiceValue, currentState.turnPlayer)

        if (!movable.contains(tokenIndex)) {
            Toast.makeText(requireContext(), "This token cannot move with roll ${currentState.currentDiceValue}", Toast.LENGTH_SHORT).show()
            return
        }

        advanceToken(tokenIndex, currentState.currentDiceValue)
    }

    private fun advanceToken(tokenIndex: Int, diceValue: Int) {
        if (isSubmittingMove) return
        val currentState = if (isOnlineMode) gameState else localGameState
        val activePlayer = currentState.turnPlayer

        val p1Tokens = currentState.player1Tokens.toMutableList()
        val p2Tokens = currentState.player2Tokens.toMutableList()

        val movingTokens = if (activePlayer == 1) p1Tokens else p2Tokens
        val opponentTokens = if (activePlayer == 1) p2Tokens else p1Tokens

        val oldPos = movingTokens[tokenIndex]
        val newPos = LudoLogic.calculateNewPosition(oldPos, diceValue, activePlayer)
        movingTokens[tokenIndex] = newPos

        triggerHaptic()

        // Check if opponent token is captured
        var capturedOpponent = false
        if (newPos in 0 until LudoLogic.TRACK_SIZE && !LudoLogic.isSafeSquare(newPos)) {
            for (i in opponentTokens.indices) {
                if (opponentTokens[i] == newPos) {
                    opponentTokens[i] = LudoLogic.YARD_POS // Captured back to yard!
                    capturedOpponent = true
                }
            }
        }

        // Check victory
        val hasWon = LudoLogic.isWin(movingTokens)
        var newStatus = "PLAYING"
        var winnerUid: String? = null

        if (hasWon) {
            newStatus = "FINISHED"
            winnerUid = if (isOnlineMode) currentUserId else (if (activePlayer == 1) "Player 1" else "Player 2")
        }

        // Turn switching logic:
        // Rolling 6 or capturing opponent grants a bonus roll!
        val getsBonusRoll = (diceValue == 6 || capturedOpponent) && !hasWon
        val nextPlayer = if (getsBonusRoll) activePlayer else 3 - activePlayer
        val nextUid = if (isOnlineMode) {
            if (nextPlayer == 1) currentState.player1Uid else currentState.player2Uid
        } else ""

        val activePlayerName = if (isOnlineMode) {
            if (activePlayer == 1) currentUserName else partnerName
        } else {
            if (activePlayer == 1) "Player 1" else "Player 2"
        }

        val actionMsg = when {
            hasWon -> "👑 $activePlayerName has brought all 4 tokens into the Heartland! VICTORY! 💖"
            capturedOpponent -> "💘 $activePlayerName captured partner's token! BONUS ROLL! 🎲"
            diceValue == 6 -> "🎉 $activePlayerName rolled a 6! BONUS ROLL! 🎲"
            else -> "$activePlayerName advanced token. Partner's turn! 🌸"
        }

        val updatedState = currentState.copy(
            player1Tokens = p1Tokens,
            player2Tokens = p2Tokens,
            turnPlayer = nextPlayer,
            turnUid = nextUid,
            currentDiceValue = 0,
            hasRolledDice = false,
            status = newStatus,
            winnerUid = winnerUid,
            lastActionMessage = actionMsg,
            moveCount = currentState.moveCount + 1,
            lastMoveTimestamp = System.currentTimeMillis()
        )

        commitState(updatedState)

        if (hasWon) {
            triggerVictoryHaptic()
            showVictoryDialog(activePlayerName, updatedState.wager)
        }
    }

    private fun commitState(newState: LudoState) {
        if (isOnlineMode) {
            isSubmittingMove = true
            gameState = newState
            renderState(newState)

            lifecycleScope.launch {
                val success = repository.updateLudoState(coupleId, newState)
                isSubmittingMove = false
                if (!success) {
                    Toast.makeText(requireContext(), "Failed to sync Ludo duel with partner. Check connection.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            localGameState = newState
            renderState(newState)
        }
    }

    private fun startNewOnlineGame() {
        if (coupleId.isBlank()) return
        isSubmittingMove = false

        val prevP1Uid = gameState.player1Uid
        val nextP1Uid = if (prevP1Uid == currentUserId) partnerId.ifBlank { currentUserId } else currentUserId
        val nextP1Name = if (nextP1Uid == currentUserId) currentUserName else partnerName
        val nextP2Uid = if (nextP1Uid == currentUserId) partnerId.ifBlank { currentUserId } else currentUserId
        val nextP2Name = if (nextP1Uid == currentUserId) partnerName else currentUserName

        val newState = LudoState(
            id = "ludo",
            player1Uid = nextP1Uid,
            player1Name = nextP1Name,
            player2Uid = nextP2Uid,
            player2Name = nextP2Name,
            turnUid = nextP1Uid,
            turnPlayer = 1,
            player1Tokens = listOf(-1, -1, -1, -1),
            player2Tokens = listOf(-1, -1, -1, -1),
            currentDiceValue = 0,
            hasRolledDice = false,
            wager = gameState.wager.ifBlank { "Winner gets breakfast in bed ☕" },
            lastActionMessage = "New duel! $nextP1Name rolls first 🎲",
            status = "PLAYING"
        )
        gameState = newState
        renderState(newState)

        lifecycleScope.launch {
            val ok = repository.updateLudoState(coupleId, newState)
            if (ok) {
                Toast.makeText(requireContext(), "New Ludo Duel Started! 🎲", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetLocalGame() {
        localGameState = LudoState(
            id = "ludo",
            player1Name = "Player 1 (Rose Hearts)",
            player2Name = "Player 2 (Sky Stars)",
            player1Tokens = listOf(-1, -1, -1, -1),
            player2Tokens = listOf(-1, -1, -1, -1),
            turnPlayer = 1,
            currentDiceValue = 0,
            hasRolledDice = false,
            wager = "Winner gets breakfast in bed ☕",
            lastActionMessage = "Duel reset! Player 1 rolls first 🎲",
            status = "PLAYING"
        )
        renderState(localGameState)
        Toast.makeText(requireContext(), "Pass & Play Ludo reset! Player 1 to roll 🎲", Toast.LENGTH_SHORT).show()
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
            hint = "e.g. Winner gets breakfast in bed ☕"
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
                repository.updateLudoState(coupleId, updated)
            }
        } else {
            localGameState = localGameState.copy(wager = newWager)
        }
    }

    private fun showVictoryDialog(winner: String, wager: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("💖 Heartland Victory!")
            .setMessage("$winner reached the Love Castle first! 🌸\n\nRomantic Reward: $wager")
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

    private fun triggerDiceHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(80)
            }
        } catch (_: Exception) {}
    }

    private fun triggerVictoryHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 150, 80, 250), -1)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 80, 250), -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(250)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
        gameListener?.remove()
        gameListener = null
    }
}
