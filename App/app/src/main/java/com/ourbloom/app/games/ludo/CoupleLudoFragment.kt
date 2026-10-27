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
    private lateinit var btnFlipBoard: ImageButton
    private lateinit var tvGameModeStatus: TextView
    private lateinit var tvActiveWager: TextView
    private lateinit var tvTopPlayerIcon: TextView
    private lateinit var tvTopPlayerName: TextView
    private lateinit var tvTopPlayerHomeCount: TextView
    private lateinit var tvTopPlayerTurnChip: TextView
    private lateinit var tvBottomPlayerIcon: TextView
    private lateinit var tvBottomPlayerName: TextView
    private lateinit var tvBottomPlayerHomeCount: TextView
    private lateinit var tvBottomPlayerTurnChip: TextView
    private lateinit var btnRollDice: MaterialCardView
    private lateinit var tvDiceDisplay: TextView
    private lateinit var tvDiceHint: TextView
    private lateinit var tvLudoActionMessage: TextView

    private var manualFlipOverride: Boolean? = null

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
        btnFlipBoard = view.findViewById(R.id.btn_flip_board)
        tvGameModeStatus = view.findViewById(R.id.tv_game_mode_status)
        tvActiveWager = view.findViewById(R.id.tv_active_wager)
        tvTopPlayerIcon = view.findViewById(R.id.tv_top_player_icon)
        tvTopPlayerName = view.findViewById(R.id.tv_top_player_name)
        tvTopPlayerHomeCount = view.findViewById(R.id.tv_top_player_home_count)
        tvTopPlayerTurnChip = view.findViewById(R.id.tv_top_player_turn_chip)
        tvBottomPlayerIcon = view.findViewById(R.id.tv_bottom_player_icon)
        tvBottomPlayerName = view.findViewById(R.id.tv_bottom_player_name)
        tvBottomPlayerHomeCount = view.findViewById(R.id.tv_bottom_player_home_count)
        tvBottomPlayerTurnChip = view.findViewById(R.id.tv_bottom_player_turn_chip)
        btnRollDice = view.findViewById(R.id.btn_roll_dice)
        tvDiceDisplay = view.findViewById(R.id.tv_dice_display)
        tvDiceHint = view.findViewById(R.id.tv_dice_hint)
        tvLudoActionMessage = view.findViewById(R.id.tv_ludo_action_message)

        view.findViewById<ImageButton>(R.id.btn_back_ludo).setOnClickListener {
            findNavController().navigateUp()
        }

        btnFlipBoard.setOnClickListener {
            val currentFlipped = ludoBoardView.isFlipped
            manualFlipOverride = !currentFlipped
            val currentState = if (isOnlineMode) gameState else localGameState
            renderState(currentState)
            triggerHaptic()
            Toast.makeText(
                requireContext(),
                if (ludoBoardView.isFlipped) "Board flipped (Golden team at bottom) 🔄" else "Board flipped (Rose team at bottom) 🔄",
                Toast.LENGTH_SHORT
            ).show()
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
        } else {
            tvGameModeStatus.text = "Pass & Play 📱"
            tvGameModeStatus.setTextColor(0xFF1976D2.toInt())
        }
        val currentState = if (isOnlineMode) gameState else localGameState
        renderState(currentState)
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

            val partnerMoved = state.moveCount > gameState.moveCount && !isSubmittingMove && gameState.moveCount > 0
            if (partnerMoved) {
                var movingPlayer = 0
                var movedTokenIndex = -1
                var oldPos = -1
                var newPos = -1

                for (i in 0..3) {
                    if (gameState.player1Tokens.getOrNull(i) != state.player1Tokens.getOrNull(i)) {
                        movingPlayer = 1
                        movedTokenIndex = i
                        oldPos = gameState.player1Tokens.getOrNull(i) ?: -1
                        newPos = state.player1Tokens.getOrNull(i) ?: -1
                        break
                    }
                }
                if (movingPlayer == 0) {
                    for (i in 0..3) {
                        if (gameState.player2Tokens.getOrNull(i) != state.player2Tokens.getOrNull(i)) {
                            movingPlayer = 2
                            movedTokenIndex = i
                            oldPos = gameState.player2Tokens.getOrNull(i) ?: -1
                            newPos = state.player2Tokens.getOrNull(i) ?: -1
                            break
                        }
                    }
                }

                if (movingPlayer != 0 && movedTokenIndex != -1 && oldPos != -1 && newPos != -1) {
                    val opponentPlayer = 3 - movingPlayer
                    val oldOppTokens = if (opponentPlayer == 1) gameState.player1Tokens else gameState.player2Tokens
                    val newOppTokens = if (opponentPlayer == 1) state.player1Tokens else state.player2Tokens
                    var capturedTokenIdx: Int? = null
                    for (i in 0..3) {
                        if (oldOppTokens.getOrNull(i) != LudoLogic.YARD_POS && newOppTokens.getOrNull(i) == LudoLogic.YARD_POS) {
                            capturedTokenIdx = i
                            break
                        }
                    }

                    val path = if (oldPos == LudoLogic.YARD_POS) {
                        listOf(newPos)
                    } else {
                        val dist = (LudoLogic.getRelativeDistanceTraveled(newPos, movingPlayer) -
                                   LudoLogic.getRelativeDistanceTraveled(oldPos, movingPlayer) +
                                   LudoLogic.TRACK_SIZE) % LudoLogic.TRACK_SIZE
                        val steps = if (dist in 1..6) dist else 1
                        val p = LudoLogic.getMovementPath(oldPos, steps, movingPlayer)
                        if (p.lastOrNull() == newPos) p else listOf(newPos)
                    }

                    gameState = state
                    ludoBoardView.animateTokenMove(
                        player = movingPlayer,
                        tokenIndex = movedTokenIndex,
                        path = path,
                        capturedOpponentPlayer = if (capturedTokenIdx != null) opponentPlayer else null,
                        capturedOpponentTokenIndex = capturedTokenIdx,
                        onStepHop = {
                            triggerHaptic()
                        },
                        onComplete = {
                            renderState(state)
                        }
                    )
                    return@observeLudoState
                }
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

        val isMePlayer1 = if (isOnlineMode) {
            if (state.player1Uid.isNotBlank() && state.player1Uid == currentUserId) true
            else if (state.player2Uid.isNotBlank() && state.player2Uid == currentUserId) false
            else true
        } else {
            true
        }

        // Auto-perspective alignment:
        // When playing as Player 2 (Sky Stars ⭐️), board naturally defaults to flipped so Sky is at the bottom.
        val defaultFlipped = if (isOnlineMode) !isMePlayer1 else false
        val effectiveFlipped = manualFlipOverride ?: defaultFlipped
        ludoBoardView.isFlipped = effectiveFlipped

        // The flip board option disappears once the first move of the game is made
        val canFlip = state.moveCount == 0
        btnFlipBoard.visibility = if (canFlip) View.VISIBLE else View.GONE

        val p1Home = state.player1Tokens.count { it == LudoLogic.HOME_POS }
        val p2Home = state.player2Tokens.count { it == LudoLogic.HOME_POS }
        val isP1Turn = state.turnPlayer == 1

        val isMyTurn = if (isOnlineMode) {
            (isP1Turn && isMePlayer1) || (!isP1Turn && !isMePlayer1)
        } else {
            if (!effectiveFlipped) isP1Turn else !isP1Turn
        }

        // Player Badges, Names, and Colors:
        // Local device owner ("You") is ALWAYS rendered at the bottom banner,
        // and the partner is ALWAYS rendered at the top banner,
        // with the respective team colors showing on each player's side.
        if (isOnlineMode) {
            if (isMePlayer1) {
                // Bottom: You (Rose Hearts ♡)
                tvBottomPlayerName.text = "You ($currentUserName) ♡"
                tvBottomPlayerIcon.text = "♡"
                tvBottomPlayerIcon.setTextColor(0xFFE91E63.toInt())
                tvBottomPlayerIcon.setBackgroundResource(R.drawable.bg_vault_card)
                tvBottomPlayerHomeCount.text = "$p1Home/4 in Heartland 💖"
                tvBottomPlayerHomeCount.setTextColor(0xFFAD1457.toInt())

                tvBottomPlayerTurnChip.text = if (isP1Turn) "Your Turn 🎲" else "Waiting ⏳"
                tvBottomPlayerTurnChip.setTextColor(if (isP1Turn) 0xFF2E7D32.toInt() else 0xFF78909C.toInt())

                // Top: Partner (Golden Stars ⭐️)
                tvTopPlayerName.text = "$partnerName ⭐️"
                tvTopPlayerIcon.text = "★"
                tvTopPlayerIcon.setTextColor(0xFFF57F17.toInt())
                tvTopPlayerIcon.setBackgroundResource(R.drawable.bg_pill_dark)
                tvTopPlayerHomeCount.text = "$p2Home/4 in Heartland 💖"
                tvTopPlayerHomeCount.setTextColor(0xFFE65100.toInt())

                tvTopPlayerTurnChip.text = if (!isP1Turn) "Rolling 🎲" else "Waiting ⏳"
                tvTopPlayerTurnChip.setTextColor(if (!isP1Turn) 0xFFE65100.toInt() else 0xFF78909C.toInt())

                // Respective color on bottom side for Dice
                btnRollDice.strokeColor = 0xFFE91E63.toInt()
                tvDiceDisplay.setTextColor(0xFFE91E63.toInt())
                tvDiceHint.setTextColor(0xFFAD1457.toInt())
            } else {
                // Bottom: You (Golden Stars ⭐️)
                tvBottomPlayerName.text = "You ($currentUserName) ⭐️"
                tvBottomPlayerIcon.text = "★"
                tvBottomPlayerIcon.setTextColor(0xFFF57F17.toInt())
                tvBottomPlayerIcon.setBackgroundResource(R.drawable.bg_pill_dark)
                tvBottomPlayerHomeCount.text = "$p2Home/4 in Heartland 💖"
                tvBottomPlayerHomeCount.setTextColor(0xFFE65100.toInt())

                tvBottomPlayerTurnChip.text = if (!isP1Turn) "Your Turn 🎲" else "Waiting ⏳"
                tvBottomPlayerTurnChip.setTextColor(if (!isP1Turn) 0xFF2E7D32.toInt() else 0xFF78909C.toInt())

                // Top: Partner (Rose Hearts ♡)
                tvTopPlayerName.text = "$partnerName ♡"
                tvTopPlayerIcon.text = "♡"
                tvTopPlayerIcon.setTextColor(0xFFE91E63.toInt())
                tvTopPlayerIcon.setBackgroundResource(R.drawable.bg_vault_card)
                tvTopPlayerHomeCount.text = "$p1Home/4 in Heartland 💖"
                tvTopPlayerHomeCount.setTextColor(0xFFAD1457.toInt())

                tvTopPlayerTurnChip.text = if (isP1Turn) "Rolling 🎲" else "Waiting ⏳"
                tvTopPlayerTurnChip.setTextColor(if (isP1Turn) 0xFFE91E63.toInt() else 0xFF78909C.toInt())

                // Respective color on bottom side for Dice
                btnRollDice.strokeColor = 0xFFF57F17.toInt()
                tvDiceDisplay.setTextColor(0xFFF57F17.toInt())
                tvDiceHint.setTextColor(0xFFE65100.toInt())
            }
        } else {
            // Pass & Play Mode
            if (!effectiveFlipped) {
                // Bottom is Player 1 (Rose)
                tvBottomPlayerName.text = "Player 1 (Rose Hearts ♡)"
                tvBottomPlayerIcon.text = "♡"
                tvBottomPlayerIcon.setTextColor(0xFFE91E63.toInt())
                tvBottomPlayerIcon.setBackgroundResource(R.drawable.bg_vault_card)
                tvBottomPlayerHomeCount.text = "$p1Home/4 in Heartland 💖"
                tvBottomPlayerHomeCount.setTextColor(0xFFAD1457.toInt())
                tvBottomPlayerTurnChip.text = if (isP1Turn) "Rolling 🎲" else "Waiting ⏳"
                tvBottomPlayerTurnChip.setTextColor(if (isP1Turn) 0xFF2E7D32.toInt() else 0xFF78909C.toInt())

                // Top is Player 2 (Golden)
                tvTopPlayerName.text = "Player 2 (Golden Stars ⭐️)"
                tvTopPlayerIcon.text = "★"
                tvTopPlayerIcon.setTextColor(0xFFF57F17.toInt())
                tvTopPlayerIcon.setBackgroundResource(R.drawable.bg_pill_dark)
                tvTopPlayerHomeCount.text = "$p2Home/4 in Heartland 💖"
                tvTopPlayerHomeCount.setTextColor(0xFFE65100.toInt())
                tvTopPlayerTurnChip.text = if (!isP1Turn) "Rolling 🎲" else "Waiting ⏳"
                tvTopPlayerTurnChip.setTextColor(if (!isP1Turn) 0xFFE65100.toInt() else 0xFF78909C.toInt())

                val isCurrentRose = isP1Turn
                val accentColor = if (isCurrentRose) 0xFFE91E63.toInt() else 0xFFF57F17.toInt()
                btnRollDice.strokeColor = accentColor
                tvDiceDisplay.setTextColor(accentColor)
                tvDiceHint.setTextColor(if (isCurrentRose) 0xFFAD1457.toInt() else 0xFFE65100.toInt())
            } else {
                // Flipped: Bottom is Player 2 (Golden)
                tvBottomPlayerName.text = "Player 2 (Golden Stars ⭐️)"
                tvBottomPlayerIcon.text = "★"
                tvBottomPlayerIcon.setTextColor(0xFFF57F17.toInt())
                tvBottomPlayerIcon.setBackgroundResource(R.drawable.bg_pill_dark)
                tvBottomPlayerHomeCount.text = "$p2Home/4 in Heartland 💖"
                tvBottomPlayerHomeCount.setTextColor(0xFFE65100.toInt())
                tvBottomPlayerTurnChip.text = if (!isP1Turn) "Rolling 🎲" else "Waiting ⏳"
                tvBottomPlayerTurnChip.setTextColor(if (!isP1Turn) 0xFF2E7D32.toInt() else 0xFF78909C.toInt())

                // Top is Player 1 (Rose)
                tvTopPlayerName.text = "Player 1 (Rose Hearts ♡)"
                tvTopPlayerIcon.text = "♡"
                tvTopPlayerIcon.setTextColor(0xFFE91E63.toInt())
                tvTopPlayerIcon.setBackgroundResource(R.drawable.bg_vault_card)
                tvTopPlayerHomeCount.text = "$p1Home/4 in Heartland 💖"
                tvTopPlayerHomeCount.setTextColor(0xFFAD1457.toInt())
                tvTopPlayerTurnChip.text = if (isP1Turn) "Rolling 🎲" else "Waiting ⏳"
                tvTopPlayerTurnChip.setTextColor(if (isP1Turn) 0xFFE91E63.toInt() else 0xFF78909C.toInt())

                val isCurrentRose = isP1Turn
                val accentColor = if (isCurrentRose) 0xFFE91E63.toInt() else 0xFFF57F17.toInt()
                btnRollDice.strokeColor = accentColor
                tvDiceDisplay.setTextColor(accentColor)
                tvDiceHint.setTextColor(if (isCurrentRose) 0xFFAD1457.toInt() else 0xFFE65100.toInt())
            }
        }

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
            val activeUid = if (activePlayer == 1) currentState.player1Uid else currentState.player2Uid
            if (activeUid == currentUserId) "You" else partnerName
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
        val path = LudoLogic.getMovementPath(oldPos, diceValue, activePlayer)
        val newPos = path.lastOrNull() ?: LudoLogic.calculateNewPosition(oldPos, diceValue, activePlayer)
        movingTokens[tokenIndex] = newPos

        // Check if opponent token is captured
        var capturedOpponent = false
        var capturedTokenIdx: Int? = null
        if (newPos in 0 until LudoLogic.TRACK_SIZE && !LudoLogic.isSafeSquare(newPos)) {
            for (i in opponentTokens.indices) {
                if (opponentTokens[i] == newPos) {
                    opponentTokens[i] = LudoLogic.YARD_POS // Captured back to yard!
                    capturedOpponent = true
                    capturedTokenIdx = i
                    break
                }
            }
        }

        // Check victory
        val hasWon = LudoLogic.isWin(movingTokens)
        var newStatus = "PLAYING"
        var winnerUid: String? = null

        if (hasWon) {
            newStatus = "FINISHED"
            winnerUid = if (isOnlineMode) {
                if (activePlayer == 1) currentState.player1Uid else currentState.player2Uid
            } else {
                if (activePlayer == 1) "Player 1" else "Player 2"
            }
        }

        // Turn switching logic:
        // Rolling 6 or capturing opponent grants a bonus roll!
        val getsBonusRoll = (diceValue == 6 || capturedOpponent) && !hasWon
        val nextPlayer = if (getsBonusRoll) activePlayer else 3 - activePlayer
        val nextUid = if (isOnlineMode) {
            if (nextPlayer == 1) currentState.player1Uid else currentState.player2Uid
        } else ""

        val activePlayerName = if (isOnlineMode) {
            val activeUid = if (activePlayer == 1) currentState.player1Uid else currentState.player2Uid
            if (activeUid == currentUserId) "You" else partnerName
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

        // Clear movable tokens immediately during hop animation
        ludoBoardView.movableTokens = emptyList()
        isSubmittingMove = true

        ludoBoardView.animateTokenMove(
            player = activePlayer,
            tokenIndex = tokenIndex,
            path = path,
            capturedOpponentPlayer = if (capturedOpponent) (3 - activePlayer) else null,
            capturedOpponentTokenIndex = capturedTokenIdx,
            onStepHop = {
                triggerHaptic()
            },
            onComplete = {
                isSubmittingMove = false
                commitState(updatedState)

                if (hasWon) {
                    triggerVictoryHaptic()
                    val isMeWinner = (winnerUid == currentUserId)
                    val winnerTitle = if (isOnlineMode) (if (isMeWinner) "You" else partnerName) else activePlayerName
                    showVictoryDialog(winnerTitle, updatedState.wager)
                }
            }
        )
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
        manualFlipOverride = null

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
        manualFlipOverride = null
        localGameState = LudoState(
            id = "ludo",
            player1Name = "Player 1 (Rose Hearts)",
            player2Name = "Player 2 (Golden Stars)",
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
