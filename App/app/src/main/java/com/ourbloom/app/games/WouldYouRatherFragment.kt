package com.ourbloom.app.games

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
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.games.models.WouldYouRatherQuestion
import com.ourbloom.app.games.models.WouldYouRatherSyncState
import kotlinx.coroutines.launch

class WouldYouRatherFragment : Fragment() {

    private val repository = FirestoreRepository()
    private var coupleId: String = ""
    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var partnerId: String = ""
    private var partnerName: String = "Sweetheart"

    private var isOnlineMode: Boolean = true
    private var syncState = WouldYouRatherSyncState()
    private var syncListener: ListenerRegistration? = null

    // Local Pass & Play State
    private var localIndex = 0
    private var localChoice: String? = null

    private lateinit var tvModeStatus: TextView
    private lateinit var tvSyncScore: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvStatusIcon: TextView
    private lateinit var tvStatusMsg: TextView

    private lateinit var cardOptionA: MaterialCardView
    private lateinit var tvOptionAText: TextView
    private lateinit var tvOptionAVoters: TextView

    private lateinit var cardOptionB: MaterialCardView
    private lateinit var tvOptionBText: TextView
    private lateinit var tvOptionBVoters: TextView

    private lateinit var cardResult: MaterialCardView
    private lateinit var tvResultText: TextView
    private lateinit var btnNext: MaterialButton

    private val questions = listOf(
        WouldYouRatherQuestion("wyr_1", "Morning coffee & cozy cuddles in bed ☕🛌", "Late-night spontaneous drive for ice cream 🚗🍦"),
        WouldYouRatherQuestion("wyr_2", "A quiet home-cooked candlelit dinner 🕯️🍝", "A fancy rooftop restaurant with skyline views 🌆🥂"),
        WouldYouRatherQuestion("wyr_3", "A week in a snow cabin by the fireplace 🏔️❄️", "A week at a tropical private beach villa 🏝️🌊"),
        WouldYouRatherQuestion("wyr_4", "Spontaneous road trip with zero plans 🗺️🚗", "Carefully curated luxury resort holiday ✈️🌴"),
        WouldYouRatherQuestion("wyr_5", "Binge-watching our favorite series in pajamas 🎬🍿", "Dressing up and dancing all night together 💃🕺"),
        WouldYouRatherQuestion("wyr_6", "Knowing exactly what your partner thinks 🧠💭", "Being delightfully surprised by them constantly 🎁💖"),
        WouldYouRatherQuestion("wyr_7", "Reliving our magical very first date together 💌", "Sneak peeking into our dream home 10 years from now 🏡"),
        WouldYouRatherQuestion("wyr_8", "Holding hands everywhere in public 🤝", "Secret affectionate glances and private cuddles only 🤫"),
        WouldYouRatherQuestion("wyr_9", "Wearing cute matching couple outfits on vacation 👕👚", "Being the dedicated personal photographer for each other 📸"),
        WouldYouRatherQuestion("wyr_10", "A sweet surprise gift every single week 🎁", "One grand unforgettable anniversary surprise trip a year ✈️"),
        WouldYouRatherQuestion("wyr_11", "Always cooking together while playing music 🍳🎵", "Always ordering delicious late-night takeout in bed 🍕🛌"),
        WouldYouRatherQuestion("wyr_12", "Watching the sunrise together early morning 🌅", "Stargazing late at night on a quiet terrace 🌌"),
        WouldYouRatherQuestion("wyr_13", "Never having to wash dishes ever again 🍽️", "Never having to fold laundry ever again 🧺"),
        WouldYouRatherQuestion("wyr_14", "Adopting two fluffy golden retriever puppies 🐶", "Adopting two cuddly sleepy kittens 🐱"),
        WouldYouRatherQuestion("wyr_15", "A 30-minute foot & back massage from your partner 💆", "Your partner doing all house chores for an entire week 🧹"),
        WouldYouRatherQuestion("wyr_16", "Kissing passionately in the pouring rain 🌧️💋", "Sharing warm hot chocolate under one big blanket ☕🧣"),
        WouldYouRatherQuestion("wyr_17", "Whispering sweet love notes in each other's ears all day 💌", "Playfully teasing and bantering like best friends 😂"),
        WouldYouRatherQuestion("wyr_18", "Sleeping in separate beds when someone kicks or snores 🛏️", "Always sleeping tangled in each other's arms no matter what 🫂")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_would_you_rather, container, false)
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
                    renderLocalQuestion()
                }
            } else {
                isOnlineMode = false
                updateModeUI()
                renderLocalQuestion()
            }
        }
    }

    private fun initViews(view: View) {
        tvModeStatus = view.findViewById(R.id.tv_wyr_mode_status)
        tvSyncScore = view.findViewById(R.id.tv_wyr_sync_score)
        tvProgress = view.findViewById(R.id.tv_wyr_progress)
        tvStatusIcon = view.findViewById(R.id.tv_wyr_status_icon)
        tvStatusMsg = view.findViewById(R.id.tv_wyr_status_msg)

        cardOptionA = view.findViewById(R.id.card_option_a)
        tvOptionAText = view.findViewById(R.id.tv_option_a_text)
        tvOptionAVoters = view.findViewById(R.id.tv_option_a_voters)

        cardOptionB = view.findViewById(R.id.card_option_b)
        tvOptionBText = view.findViewById(R.id.tv_option_b_text)
        tvOptionBVoters = view.findViewById(R.id.tv_option_b_voters)

        cardResult = view.findViewById(R.id.card_wyr_result)
        tvResultText = view.findViewById(R.id.tv_wyr_result_text)
        btnNext = view.findViewById(R.id.btn_next_wyr)

        view.findViewById<ImageButton>(R.id.btn_back_wyr).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<ImageButton>(R.id.btn_switch_wyr_mode).setOnClickListener {
            toggleGameMode()
        }

        cardOptionA.setOnClickListener {
            onOptionClicked("A")
        }

        cardOptionB.setOnClickListener {
            onOptionClicked("B")
        }

        btnNext.setOnClickListener {
            onNextClicked()
        }
    }

    private fun updateModeUI() {
        if (isOnlineMode) {
            tvModeStatus.text = "Online with $partnerName 💖"
            tvModeStatus.setTextColor(0xFFE85D75.toInt())
        } else {
            tvModeStatus.text = "Pass & Play (Same Phone) 📱"
            tvModeStatus.setTextColor(0xFF1976D2.toInt())
        }
    }

    private fun toggleGameMode() {
        if (coupleId.isBlank()) {
            Toast.makeText(requireContext(), "Connect partner profile for online sync", Toast.LENGTH_SHORT).show()
            return
        }
        isOnlineMode = !isOnlineMode
        updateModeUI()
        if (isOnlineMode) {
            startOnlineListener()
            Toast.makeText(requireContext(), "Switched to Online Sync with $partnerName 💖", Toast.LENGTH_SHORT).show()
        } else {
            syncListener?.remove()
            renderLocalQuestion()
            Toast.makeText(requireContext(), "Switched to Pass & Play on this phone 📱", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // ONLINE MULTIPLAYER (FIRESTORE)
    // ==========================================

    private fun startOnlineListener() {
        if (coupleId.isBlank()) return
        syncListener?.remove()
        syncListener = repository.observeWouldYouRatherState(coupleId) { state ->
            if (!isAdded) return@observeWouldYouRatherState
            syncState = state
            renderOnlineState(state)
        }
    }

    private fun renderOnlineState(state: WouldYouRatherSyncState) {
        val qIndex = state.questionIndex % questions.size
        val q = questions[qIndex]
        tvProgress.text = "Dilemma ${qIndex + 1} of ${questions.size} • Romantic Sync"
        tvOptionAText.text = q.optionA
        tvOptionBText.text = q.optionB

        // Render score
        val percentage = if (state.totalAnswered > 0) {
            (state.matchCount * 100) / state.totalAnswered
        } else {
            0
        }
        tvSyncScore.text = "Sync: ${state.matchCount}/${state.totalAnswered} ($percentage%)"

        val isPlayer1 = state.player1Uid == currentUserId || (state.player1Uid.isBlank() && currentUserId <= partnerId)
        val myChoice = if (isPlayer1) state.player1Choice else state.player2Choice
        val partnerChoice = if (isPlayer1) state.player2Choice else state.player1Choice

        // Reset option borders
        resetCardBorders()

        if (state.isRevealed) {
            // BOTH HAVE VOTED: SHOW REVEAL!
            highlightRevealedChoices(myChoice, partnerChoice)

            val isMatch = state.player1Choice.isNotBlank() && state.player1Choice == state.player2Choice
            cardResult.visibility = View.VISIBLE

            if (isMatch) {
                cardResult.setCardBackgroundColor(0xFFE8F5E9.toInt())
                cardResult.strokeColor = 0xFF4CAF50.toInt()
                val pickedText = if (state.player1Choice == "A") "Option A" else "Option B"
                tvResultText.text = "💖 100% In Sync! You both picked $pickedText! 🎉"
                tvResultText.setTextColor(0xFF2E7D32.toInt())
                tvStatusIcon.text = "💖"
                tvStatusMsg.text = "You are completely in sync! Tap 'Next Dilemma' to continue."
                tvStatusMsg.setTextColor(0xFF2E7D32.toInt())
            } else {
                cardResult.setCardBackgroundColor(0xFFEDE7F6.toInt())
                cardResult.strokeColor = 0xFF7E57C2.toInt()
                tvResultText.text = "⚡ Opposites Attract! You picked Option $myChoice, $partnerName picked Option $partnerChoice!"
                tvResultText.setTextColor(0xFF512DA8.toInt())
                tvStatusIcon.text = "⚡"
                tvStatusMsg.text = "Different choices make love interesting! Tap 'Next Dilemma' to continue."
                tvStatusMsg.setTextColor(0xFF512DA8.toInt())
            }

            btnNext.visibility = View.VISIBLE
        } else {
            // STILL IN VOTING PHASE
            cardResult.visibility = View.GONE
            tvOptionAVoters.visibility = View.GONE
            tvOptionBVoters.visibility = View.GONE

            if (myChoice.isNotBlank()) {
                // I have voted, waiting for partner
                highlightSingleChoice(myChoice)
                tvStatusIcon.text = "🔒"
                tvStatusMsg.text = "Your answer is locked! Waiting for $partnerName to choose... ⏳"
                tvStatusMsg.setTextColor(0xFF0D47A1.toInt())
                btnNext.visibility = View.GONE
            } else {
                // I haven't voted yet
                if (partnerChoice.isNotBlank()) {
                    tvStatusIcon.text = "🔮"
                    tvStatusMsg.text = "$partnerName has locked their answer! Choose Option A or B to reveal match!"
                    tvStatusMsg.setTextColor(0xFFC2185B.toInt())
                } else {
                    tvStatusIcon.text = "✨"
                    tvStatusMsg.text = "Choose your answer secretly to see if you match!"
                    tvStatusMsg.setTextColor(0xFF0D47A1.toInt())
                }
                btnNext.visibility = View.GONE
            }
        }
    }

    private fun onOptionClicked(choice: String) {
        triggerHaptic()

        if (isOnlineMode) {
            handleOnlineOptionClick(choice)
        } else {
            handleLocalOptionClick(choice)
        }
    }

    private fun handleOnlineOptionClick(choice: String) {
        if (syncState.isRevealed) {
            Toast.makeText(requireContext(), "Already revealed! Tap 'Next Dilemma' for the next question 🌸", Toast.LENGTH_SHORT).show()
            return
        }

        val isPlayer1 = syncState.player1Uid == currentUserId || (syncState.player1Uid.isBlank() && currentUserId <= partnerId)
        val currentMyChoice = if (isPlayer1) syncState.player1Choice else syncState.player2Choice

        if (currentMyChoice.isNotBlank()) {
            Toast.makeText(requireContext(), "Your answer is already locked! Waiting for $partnerName 🔒", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedP1Uid = if (syncState.player1Uid.isBlank()) {
            if (isPlayer1) currentUserId else partnerId
        } else syncState.player1Uid

        val updatedP2Uid = if (syncState.player2Uid.isBlank()) {
            if (isPlayer1) partnerId else currentUserId
        } else syncState.player2Uid

        val newP1Choice = if (isPlayer1) choice else syncState.player1Choice
        val newP2Choice = if (!isPlayer1) choice else syncState.player2Choice

        val bothVoted = newP1Choice.isNotBlank() && newP2Choice.isNotBlank()
        val isMatch = bothVoted && newP1Choice == newP2Choice

        val newMatchCount = if (isMatch) syncState.matchCount + 1 else syncState.matchCount
        val newTotalAnswered = if (bothVoted) syncState.totalAnswered + 1 else syncState.totalAnswered

        val updated = syncState.copy(
            player1Uid = updatedP1Uid,
            player1Choice = newP1Choice,
            player2Uid = updatedP2Uid,
            player2Choice = newP2Choice,
            isRevealed = bothVoted,
            matchCount = newMatchCount,
            totalAnswered = newTotalAnswered,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            repository.updateWouldYouRatherState(coupleId, updated)
            if (bothVoted && isMatch) {
                triggerVictoryHaptic()
            }
        }
    }

    private fun onNextClicked() {
        triggerHaptic()

        if (isOnlineMode) {
            val nextIdx = (syncState.questionIndex + 1) % questions.size
            val resetForNext = syncState.copy(
                questionIndex = nextIdx,
                player1Choice = "",
                player2Choice = "",
                isRevealed = false,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
            lifecycleScope.launch {
                repository.updateWouldYouRatherState(coupleId, resetForNext)
            }
        } else {
            localIndex = (localIndex + 1) % questions.size
            localChoice = null
            renderLocalQuestion()
        }
    }

    // ==========================================
    // LOCAL PASS & PLAY LOGIC
    // ==========================================

    private fun renderLocalQuestion() {
        val q = questions[localIndex]
        tvProgress.text = "Dilemma ${localIndex + 1} of ${questions.size} • Romantic Sync"
        tvOptionAText.text = q.optionA
        tvOptionBText.text = q.optionB
        tvSyncScore.text = ""

        resetCardBorders()
        cardResult.visibility = View.GONE
        tvOptionAVoters.visibility = View.GONE
        tvOptionBVoters.visibility = View.GONE
        tvStatusIcon.text = "📱"
        tvStatusMsg.text = "Choose your answer, then ask your partner!"
        btnNext.visibility = View.VISIBLE
    }

    private fun handleLocalOptionClick(choice: String) {
        localChoice = choice
        highlightSingleChoice(choice)

        cardResult.visibility = View.VISIBLE
        cardResult.setCardBackgroundColor(0xFFE8F5E9.toInt())
        cardResult.strokeColor = 0xFF4CAF50.toInt()
        tvResultText.text = "You picked Option $choice! 💖 Pass phone to sweetheart to compare!"
        tvResultText.setTextColor(0xFF2E7D32.toInt())
    }

    // ==========================================
    // UI HELPERS
    // ==========================================

    private fun resetCardBorders() {
        cardOptionA.strokeColor = 0xFFBBDEFB.toInt()
        cardOptionA.strokeWidth = 2
        cardOptionA.setCardBackgroundColor(0xFFFFFFFF.toInt())

        cardOptionB.strokeColor = 0xFFFFCDD2.toInt()
        cardOptionB.strokeWidth = 2
        cardOptionB.setCardBackgroundColor(0xFFFFFFFF.toInt())
    }

    private fun highlightSingleChoice(choice: String) {
        if (choice == "A") {
            cardOptionA.strokeColor = 0xFF1976D2.toInt()
            cardOptionA.strokeWidth = 4
            cardOptionA.setCardBackgroundColor(0xFFE3F2FD.toInt())
        } else {
            cardOptionB.strokeColor = 0xFFE85D75.toInt()
            cardOptionB.strokeWidth = 4
            cardOptionB.setCardBackgroundColor(0xFFFFEBEE.toInt())
        }
    }

    private fun highlightRevealedChoices(myChoice: String, partnerChoice: String) {
        tvOptionAVoters.visibility = View.VISIBLE
        tvOptionBVoters.visibility = View.VISIBLE

        val aVoters = mutableListOf<String>()
        val bVoters = mutableListOf<String>()

        if (myChoice == "A") aVoters.add("You") else if (myChoice == "B") bVoters.add("You")
        if (partnerChoice == "A") aVoters.add(partnerName) else if (partnerChoice == "B") bVoters.add(partnerName)

        tvOptionAVoters.text = if (aVoters.isNotEmpty()) "✓ " + aVoters.joinToString(" & ") else ""
        tvOptionBVoters.text = if (bVoters.isNotEmpty()) "✓ " + bVoters.joinToString(" & ") else ""

        if (aVoters.isNotEmpty()) {
            cardOptionA.strokeColor = 0xFF1976D2.toInt()
            cardOptionA.strokeWidth = 3
            cardOptionA.setCardBackgroundColor(0xFFE3F2FD.toInt())
        }
        if (bVoters.isNotEmpty()) {
            cardOptionB.strokeColor = 0xFFE85D75.toInt()
            cardOptionB.strokeWidth = 3
            cardOptionB.setCardBackgroundColor(0xFFFFEBEE.toInt())
        }
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
                val pattern = longArrayOf(0, 150, 100, 200)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(300)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        syncListener?.remove()
    }
}
