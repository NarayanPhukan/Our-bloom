package com.ourbloom.app.games

import android.os.Bundle
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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.ListenerRegistration
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.games.models.CustomTruthOrDareCard
import com.ourbloom.app.games.models.TruthOrDareCard
import com.ourbloom.app.games.models.TruthOrDareSyncState
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

class TruthOrDareFragment : Fragment() {

    private lateinit var tvTodBadge: TextView
    private lateinit var tvTodCategoryBadge: TextView
    private lateinit var tvTodText: TextView
    private lateinit var tvTodTurnInfo: TextView
    private lateinit var tvTodOnlineBadge: TextView
    private lateinit var btnTodMarkDone: MaterialButton
    private lateinit var btnTodAddCustom: MaterialButton
    private lateinit var cardTodPrompt: MaterialCardView
    private lateinit var chipGroupCategory: ChipGroup

    private val repository = FirestoreRepository()
    private var coupleId: String = ""
    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var partnerId: String = ""
    private var partnerName: String = "Sweetheart"
    private var isOnlineMode: Boolean = false

    private var syncListener: ListenerRegistration? = null
    private var customCardsListener: ListenerRegistration? = null

    private var selectedCategory: String = "ALL"
    private var lastDrawnIndex: Int = -1
    private var currentSyncState: TruthOrDareSyncState? = null
    private var lastRenderedTimestamp: Long = 0L

    private val customCards = mutableListOf<CustomTruthOrDareCard>()

    private val prompts = listOf(
        // SWEET (TRUTH)
        TruthOrDareCard("s_t_1", "TRUTH", "SWEET", "When was the exact moment you realized you had fallen deeply in love with me? ♡"),
        TruthOrDareCard("s_t_2", "TRUTH", "SWEET", "What is your favorite memory of us from our very first month together?"),
        TruthOrDareCard("s_t_3", "TRUTH", "SWEET", "What is the sweetest thing I've ever done that completely melted your heart?"),
        TruthOrDareCard("s_t_4", "TRUTH", "SWEET", "What is a small, quiet habit of mine that you secretly find irresistibly adorable?"),
        TruthOrDareCard("s_t_5", "TRUTH", "SWEET", "If you could freeze one single moment in time with me forever, which one would it be?"),
        TruthOrDareCard("s_t_6", "TRUTH", "SWEET", "What was the very first thought running through your head when we had our first kiss?"),
        TruthOrDareCard("s_t_7", "TRUTH", "SWEET", "What song or melody always makes you close your eyes and picture my face?"),
        TruthOrDareCard("s_t_8", "TRUTH", "SWEET", "What is something I do for you that makes you feel the most treasured person in the world?"),

        // SWEET (DARE)
        TruthOrDareCard("s_d_1", "DARE", "SWEET", "Look into your partner's eyes for 45 seconds without laughing, then give them a warm tight hug! 🌸"),
        TruthOrDareCard("s_d_2", "DARE", "SWEET", "Kiss your partner on the forehead, both cheeks, and their lips, whispering one compliment for each kiss ♡"),
        TruthOrDareCard("s_d_3", "DARE", "SWEET", "Send your partner a sweet 20-second voice note right now saying why they are your favorite person in the universe."),
        TruthOrDareCard("s_d_4", "DARE", "SWEET", "Slow dance together to an imaginary romantic song for 1 minute right where you are! 💃🕺"),
        TruthOrDareCard("s_d_5", "DARE", "SWEET", "Take your partner's hand, kiss each fingertip one by one, and tell them what you appreciate about their touch."),
        TruthOrDareCard("s_d_6", "DARE", "SWEET", "Give your partner a slow, gentle 2-minute shoulder and neck massage while whispering sweet nothings."),

        // DEEP (TRUTH)
        TruthOrDareCard("d_t_1", "TRUTH", "DEEP", "What makes you feel most safe, cherished, and deeply loved in our relationship?"),
        TruthOrDareCard("d_t_2", "TRUTH", "DEEP", "What is one dream or aspiration about our future together that you haven't shared yet?"),
        TruthOrDareCard("d_t_3", "TRUTH", "DEEP", "In what ways do you feel like we have helped each other grow into better versions of ourselves?"),
        TruthOrDareCard("d_t_4", "TRUTH", "DEEP", "What is a song, place, or scent that will always remind you of our love story?"),
        TruthOrDareCard("d_t_5", "TRUTH", "DEEP", "What is something vulnerable you wanted to tell me lately but hadn't found the right moment for?"),
        TruthOrDareCard("d_t_6", "TRUTH", "DEEP", "What do you think is our greatest strength as a team when going through rough times?"),
        TruthOrDareCard("d_t_7", "TRUTH", "DEEP", "What is one promise you silently made to yourself about our relationship that you still keep?"),

        // DEEP (DARE)
        TruthOrDareCard("d_d_1", "DARE", "DEEP", "Write down 3 promises for our future on a note and place it in their hands to read aloud."),
        TruthOrDareCard("d_d_2", "DARE", "DEEP", "Hold both of your partner's hands over your heart and tell them your favorite thing about their soul."),
        TruthOrDareCard("d_d_3", "DARE", "DEEP", "Look deep into your partner's eyes and whisper 3 things you love about them that cannot be seen on the outside."),

        // PLAYFUL (TRUTH)
        TruthOrDareCard("p_t_1", "TRUTH", "PLAYFUL", "What is the funniest or most awkward thought that crossed your mind on our first date? 😂"),
        TruthOrDareCard("p_t_2", "TRUTH", "PLAYFUL", "Who is the bigger dramatic baby when they catch a cold or don't get food on time?"),
        TruthOrDareCard("p_t_3", "TRUTH", "PLAYFUL", "If our love story was a cheesy romantic comedy movie, what ridiculous title would it have?"),
        TruthOrDareCard("p_t_4", "TRUTH", "PLAYFUL", "What is the weirdest nickname you secretly think fits me perfectly?"),
        TruthOrDareCard("p_t_5", "TRUTH", "PLAYFUL", "If I were a dessert, what would I be and how would you eat me? 🍨"),

        // PLAYFUL (DARE)
        TruthOrDareCard("p_d_1", "DARE", "PLAYFUL", "Call your partner only by silly royalty titles ('Your Highness', 'My Liege') for the next 10 minutes! 👑"),
        TruthOrDareCard("p_d_2", "DARE", "PLAYFUL", "Do your best, most dramatic impression of how your partner acts when they are sleepy or cranky!"),
        TruthOrDareCard("p_d_3", "DARE", "PLAYFUL", "Sing the chorus of our favorite song in the most exaggerated opera voice possible! 🎶"),
        TruthOrDareCard("p_d_4", "DARE", "PLAYFUL", "Let your partner style your hair however they want and take a cute selfie together!"),
        TruthOrDareCard("p_d_5", "DARE", "PLAYFUL", "Feed your partner a snack or chocolate using only your mouth! 🍫"),
        TruthOrDareCard("p_d_6", "DARE", "PLAYFUL", "Send a funny selfie right now with the goofiest kissy face you can make!"),

        // SPICY & INTIMATE (TRUTH)
        TruthOrDareCard("sp_t_1", "TRUTH", "SPICY", "What is your absolute favorite part of my body to kiss, touch, and caress? 🔥"),
        TruthOrDareCard("sp_t_2", "TRUTH", "SPICY", "What is a secret romantic or sensual fantasy you have about me that you haven't shared yet?"),
        TruthOrDareCard("sp_t_3", "TRUTH", "SPICY", "What is the hottest dream or daydream you've ever had about us being intimate together?"),
        TruthOrDareCard("sp_t_4", "TRUTH", "SPICY", "What is something I do while kissing or cuddling that drives you completely wild?"),
        TruthOrDareCard("sp_t_5", "TRUTH", "SPICY", "What kind of outfit, sleepwear, or look would drive you completely crazy if I wore it for you?"),
        TruthOrDareCard("sp_t_6", "TRUTH", "SPICY", "Where is your most sensitive erogenous zone that makes you shiver when I touch or kiss it?"),
        TruthOrDareCard("sp_t_7", "TRUTH", "SPICY", "Do you prefer slow, deeply emotional intimacy, or spontaneous, intense, breathless passion?"),
        TruthOrDareCard("sp_t_8", "TRUTH", "SPICY", "If we had the whole house to ourselves with no clothes allowed for a whole weekend, what would we do first?"),
        TruthOrDareCard("sp_t_9", "TRUTH", "SPICY", "What is your biggest instant turn-on that makes you desire me immediately?"),
        TruthOrDareCard("sp_t_10", "TRUTH", "SPICY", "Describe our most passionate kiss or intimate encounter in delicious, breathless detail."),
        TruthOrDareCard("sp_t_11", "TRUTH", "SPICY", "What dirty or naughty thought about me crossed your mind recently when you looked at me?"),
        TruthOrDareCard("sp_t_12", "TRUTH", "SPICY", "Where is the most daring or adventurous place you'd ever want to steal an intimate moment with me?"),
        TruthOrDareCard("sp_t_13", "TRUTH", "SPICY", "If you could whisper anything in my ear right now to make my heart race, what would you say?"),
        TruthOrDareCard("sp_t_14", "TRUTH", "SPICY", "What is one touch of mine that instantly melts all your resistance and makes you want me?"),
        TruthOrDareCard("sp_t_15", "TRUTH", "SPICY", "What is your secret physical guilty pleasure when we are cuddled in bed together?"),
        TruthOrDareCard("sp_t_16", "TRUTH", "SPICY", "Rate the chemistry of our last kiss on a scale of 1-10, and tell me how we can take it to 11 right now."),
        TruthOrDareCard("sp_t_17", "TRUTH", "SPICY", "What is one teasing dare you've secretly always wanted me to perform on you?"),
        TruthOrDareCard("sp_t_18", "TRUTH", "SPICY", "If you could freeze me in any position right now and do whatever you wanted with your lips, what would it be?"),
        TruthOrDareCard("sp_t_19", "TRUTH", "SPICY", "What is the most enticing thing I do without even realizing it?"),
        TruthOrDareCard("sp_t_20", "TRUTH", "SPICY", "Do you like being teased and held back, or having full, immediate passion?"),

        // SPICY & INTIMATE (DARE)
        TruthOrDareCard("sp_d_1", "DARE", "SPICY", "Give your partner a slow, passionate 60-second kiss with your hands tangled in their hair or waist. 🔥"),
        TruthOrDareCard("sp_d_2", "DARE", "SPICY", "Slowly remove one item of your partner's clothing using only your fingertips or teeth with a seductive smile."),
        TruthOrDareCard("sp_d_3", "DARE", "SPICY", "Close your eyes (or wear a blindfold) and let your partner kiss 3 sensitive intimate spots; guess each one!"),
        TruthOrDareCard("sp_d_4", "DARE", "SPICY", "Spend 60 seconds planting soft, warm kisses on your partner's neck, jawline, and earlobes without touching their lips."),
        TruthOrDareCard("sp_d_5", "DARE", "SPICY", "Whisper into your partner's ear the exact naughty, passionate thing you want to do to them tonight in bed."),
        TruthOrDareCard("sp_d_6", "DARE", "SPICY", "Bite your partner's bottom lip gently, pull them tightly by the waist, and tell them what you find irresistible about them."),
        TruthOrDareCard("sp_d_7", "DARE", "SPICY", "Sit on your partner's lap facing them, look deep into their eyes, and share a 30-second breathless kiss."),
        TruthOrDareCard("sp_d_8", "DARE", "SPICY", "Give your partner a sensual 2-minute massage on their lower back and shoulders with warm hands."),
        TruthOrDareCard("sp_d_9", "DARE", "SPICY", "Trace your fingertips slowly down your partner's chest and stomach, leaving a soft kiss trail behind."),
        TruthOrDareCard("sp_d_10", "DARE", "SPICY", "Take off one piece of your own clothing of your partner's choice with a slow, teasing smile."),
        TruthOrDareCard("sp_d_11", "DARE", "SPICY", "Let your partner leave a gentle love mark or soft kiss trail anywhere on your neck, collarbone, or shoulder."),
        TruthOrDareCard("sp_d_12", "DARE", "SPICY", "Kiss your partner everywhere EXCEPT their lips for 60 seconds, keeping them aching for a real kiss!"),
        TruthOrDareCard("sp_d_13", "DARE", "SPICY", "Give your partner a slow, teasing lap sway or dance for 30 seconds to a sultry beat."),
        TruthOrDareCard("sp_d_14", "DARE", "SPICY", "Hold your partner against the wall or bed, breathe warmly against their neck, and whisper your biggest desire."),
        TruthOrDareCard("sp_d_15", "DARE", "SPICY", "Trace an ice cube (or warm breath) along your partner's collarbone and down their spine."),
        TruthOrDareCard("sp_d_16", "DARE", "SPICY", "Blindfold your partner and give them 5 slow, lingering kisses on 5 different parts of their body."),
        TruthOrDareCard("sp_d_17", "DARE", "SPICY", "Let your partner pose you in any sensual position they desire and hold it for 30 seconds without moving."),
        TruthOrDareCard("sp_d_18", "DARE", "SPICY", "Lock lips with your partner in a passionate French kiss that lasts a full unbroken 30 seconds."),
        TruthOrDareCard("sp_d_19", "DARE", "SPICY", "Run your hands slowly under your partner's shirt, feeling their warm skin while looking into their eyes."),
        TruthOrDareCard("sp_d_20", "DARE", "SPICY", "Send your partner a sultry voice memo right now whispering the dirtiest thought you've had today.")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_truth_or_dare, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupCategoryChips()
        setupButtons(view)

        // Initialize User and Couple Session
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
                    tvTodOnlineBadge.text = "● Online & Synced with $partnerName 🌐"
                    tvTodOnlineBadge.setTextColor(0xFF4CAF50.toInt())
                    startOnlineListener()
                    startCustomCardsListener()
                } else {
                    setupOfflineMode()
                }
            } else {
                setupOfflineMode()
            }
        }
    }

    private fun setupOfflineMode() {
        isOnlineMode = false
        tvTodOnlineBadge.text = "● Pass & Play Mode (Local)"
        tvTodOnlineBadge.setTextColor(0xFF9E9E9E.toInt())
    }

    private fun initViews(view: View) {
        tvTodBadge = view.findViewById(R.id.tv_tod_badge)
        tvTodCategoryBadge = view.findViewById(R.id.tv_tod_category_badge)
        tvTodText = view.findViewById(R.id.tv_tod_text)
        tvTodTurnInfo = view.findViewById(R.id.tv_tod_turn_info)
        tvTodOnlineBadge = view.findViewById(R.id.tv_tod_online_badge)
        btnTodMarkDone = view.findViewById(R.id.btn_tod_mark_done)
        btnTodAddCustom = view.findViewById(R.id.btn_tod_add_custom)
        cardTodPrompt = view.findViewById(R.id.card_tod_prompt)
        chipGroupCategory = view.findViewById(R.id.chip_group_tod_category)
    }

    private fun setupCategoryChips() {
        chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedCategory = when (checkedIds.firstOrNull()) {
                R.id.chip_cat_endless -> "ENDLESS"
                R.id.chip_cat_spicy -> "SPICY"
                R.id.chip_cat_sweet -> "SWEET"
                R.id.chip_cat_deep -> "DEEP"
                R.id.chip_cat_playful -> "PLAYFUL"
                R.id.chip_cat_custom -> "CUSTOM"
                else -> "ALL"
            }
        }
    }

    private fun setupButtons(view: View) {
        view.findViewById<ImageButton>(R.id.btn_back_tod).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<MaterialButton>(R.id.btn_pick_truth).setOnClickListener {
            handleDrawPrompt(preferredType = "TRUTH")
        }

        view.findViewById<MaterialButton>(R.id.btn_pick_dare).setOnClickListener {
            handleDrawPrompt(preferredType = "DARE")
        }

        view.findViewById<MaterialButton>(R.id.btn_random_spin).setOnClickListener {
            handleDrawPrompt(preferredType = null)
        }

        cardTodPrompt.setOnClickListener {
            handleDrawPrompt(preferredType = null)
        }

        btnTodMarkDone.setOnClickListener {
            triggerHaptic()
            markTurnCompleted()
        }

        btnTodAddCustom.setOnClickListener {
            showAddCustomCardDialog()
        }
    }

    private fun startOnlineListener() {
        if (coupleId.isBlank()) return
        syncListener?.remove()
        syncListener = repository.observeTruthOrDareState(coupleId) { state ->
            currentSyncState = state
            if (state.currentPrompt.isNotBlank() && state.lastUpdatedTimestamp != lastRenderedTimestamp) {
                lastRenderedTimestamp = state.lastUpdatedTimestamp
                renderCardFromSync(state)
            }
        }
    }

    private fun startCustomCardsListener() {
        if (coupleId.isBlank()) return
        customCardsListener?.remove()
        customCardsListener = repository.observeCustomTruthOrDareCards(coupleId) { cards ->
            customCards.clear()
            customCards.addAll(cards)
        }
    }

    private fun handleDrawPrompt(preferredType: String?) {
        triggerHaptic()

        val cardToDraw: TruthOrDareCard = when (selectedCategory) {
            "ENDLESS" -> {
                TruthOrDareGenerator.generateCard(preferredType, "ENDLESS")
            }
            "CUSTOM" -> {
                val filteredCustom = customCards.filter { card ->
                    preferredType == null || card.type == preferredType
                }
                if (filteredCustom.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Your Secret Vault is empty! Tap '+ Secret Card ✍️' to add custom dares.",
                        Toast.LENGTH_LONG
                    ).show()
                    // Fallback to generator
                    TruthOrDareGenerator.generateCard(preferredType, "SPICY")
                } else {
                    val custom = filteredCustom.random()
                    TruthOrDareCard(
                        id = custom.id,
                        type = custom.type,
                        category = "CUSTOM",
                        prompt = custom.prompt
                    )
                }
            }
            else -> {
                // Combine built-in prompts with custom cards if in "ALL"
                val allCandidateCards = mutableListOf<TruthOrDareCard>()
                allCandidateCards.addAll(prompts)
                if (selectedCategory == "ALL") {
                    allCandidateCards.addAll(customCards.map {
                        TruthOrDareCard(it.id, it.type, "CUSTOM", it.prompt)
                    })
                }

                val filtered = allCandidateCards.filter { card ->
                    val matchCategory = selectedCategory == "ALL" || card.category == selectedCategory
                    val matchType = preferredType == null || card.type == preferredType
                    matchCategory && matchType
                }

                if (filtered.isEmpty()) {
                    TruthOrDareGenerator.generateCard(preferredType, selectedCategory)
                } else {
                    var nextIndex = Random.nextInt(filtered.size)
                    if (filtered.size > 1 && nextIndex == lastDrawnIndex) {
                        nextIndex = (nextIndex + 1) % filtered.size
                    }
                    lastDrawnIndex = nextIndex
                    filtered[nextIndex]
                }
            }
        }

        if (isOnlineMode && coupleId.isNotBlank()) {
            val newState = TruthOrDareSyncState(
                id = "truthordare",
                currentCardId = cardToDraw.id,
                currentType = cardToDraw.type,
                currentCategory = cardToDraw.category,
                currentPrompt = cardToDraw.prompt,
                drawnByUid = currentUserId,
                drawnByName = currentUserName.ifBlank { "You" },
                targetUid = partnerId,
                targetName = partnerName.ifBlank { "Sweetheart" },
                status = "ACTIVE",
                lastUpdatedTimestamp = System.currentTimeMillis(),
                turnCount = (currentSyncState?.turnCount ?: 0) + 1
            )
            lifecycleScope.launch {
                repository.updateTruthOrDareState(coupleId, newState)
            }
        } else {
            // Local / Pass & Play
            animateCardFlip(cardToDraw, isDrawnByMe = true, partnerDisplayName = "Partner")
        }
    }

    private fun renderCardFromSync(state: TruthOrDareSyncState) {
        val isDrawnByMe = state.drawnByUid == currentUserId
        val fakeCard = TruthOrDareCard(
            id = state.currentCardId,
            type = state.currentType,
            category = state.currentCategory,
            prompt = state.currentPrompt
        )
        animateCardFlip(
            card = fakeCard,
            isDrawnByMe = isDrawnByMe,
            partnerDisplayName = if (isDrawnByMe) state.targetName else state.drawnByName
        )

        if (state.status == "COMPLETED") {
            tvTodTurnInfo.text = "🎉 Round Completed! Tap below to draw the next card!"
            btnTodMarkDone.visibility = View.GONE
        } else {
            btnTodMarkDone.visibility = View.VISIBLE
        }
    }

    private fun animateCardFlip(card: TruthOrDareCard, isDrawnByMe: Boolean, partnerDisplayName: String) {
        cardTodPrompt.animate()
            .rotationY(90f)
            .setDuration(150)
            .withEndAction {
                // Update content
                tvTodText.text = card.prompt
                if (card.type == "TRUTH") {
                    tvTodBadge.text = "TRUTH 🕊️"
                    tvTodBadge.setTextColor(0xFF0288D1.toInt())
                    cardTodPrompt.strokeColor = 0xFF81D4FA.toInt()
                } else {
                    tvTodBadge.text = "DARE ⚡"
                    tvTodBadge.setTextColor(0xFFE85D75.toInt())
                    cardTodPrompt.strokeColor = 0xFFFFCDD2.toInt()
                }

                tvTodCategoryBadge.text = when (card.category) {
                    "SWEET" -> "🌸 SWEET & ROMANTIC"
                    "DEEP" -> "💭 DEEP & SOULFUL"
                    "PLAYFUL" -> "😂 FUN & PLAYFUL"
                    "SPICY" -> "🔥 SPICY & FLIRTY"
                    "ENDLESS" -> "♾️ ENDLESS GENERATOR"
                    "CUSTOM" -> "✍️ SECRET VAULT CARD"
                    else -> "✨ COUPLE DECK"
                }

                if (isOnlineMode) {
                    tvTodTurnInfo.text = if (isDrawnByMe) {
                        "Drawn by You for $partnerDisplayName 💖"
                    } else {
                        "Drawn by $partnerDisplayName for You! 💋"
                    }
                    btnTodMarkDone.visibility = View.VISIBLE
                } else {
                    tvTodTurnInfo.text = "Pass & Play • Tap Done when completed! ✨"
                    btnTodMarkDone.visibility = View.VISIBLE
                }

                cardTodPrompt.rotationY = -90f
                cardTodPrompt.animate()
                    .rotationY(0f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun markTurnCompleted() {
        btnTodMarkDone.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(120)
            .withEndAction {
                btnTodMarkDone.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                tvTodTurnInfo.text = "🎉 Round Completed! Tap below to draw next!"
                btnTodMarkDone.visibility = View.GONE
            }
            .start()

        if (isOnlineMode && coupleId.isNotBlank()) {
            val updated = currentSyncState?.copy(
                status = "COMPLETED",
                lastUpdatedTimestamp = System.currentTimeMillis()
            ) ?: return
            lifecycleScope.launch {
                repository.updateTruthOrDareState(coupleId, updated)
            }
        }
    }

    private fun showAddCustomCardDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_truth_or_dare, null)
        val toggleType = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggle_custom_type)
        val chipGroupCat = dialogView.findViewById<ChipGroup>(R.id.chip_group_custom_cat)
        val etPrompt = dialogView.findViewById<TextInputEditText>(R.id.et_custom_prompt)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add to Secret Vault ✍️🔐")
            .setView(dialogView)
            .setPositiveButton("Save to Deck 💖") { _, _ ->
                val promptText = etPrompt.text?.toString()?.trim() ?: ""
                if (promptText.isBlank()) {
                    Toast.makeText(requireContext(), "Card prompt cannot be empty!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val type = if (toggleType.checkedButtonId == R.id.btn_toggle_dare) "DARE" else "TRUTH"
                val category = when (chipGroupCat.checkedChipId) {
                    R.id.chip_custom_spicy -> "SPICY"
                    R.id.chip_custom_sweet -> "SWEET"
                    R.id.chip_custom_deep -> "DEEP"
                    R.id.chip_custom_playful -> "PLAYFUL"
                    else -> "SPICY"
                }

                val newCard = CustomTruthOrDareCard(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    category = category,
                    prompt = promptText,
                    createdByUid = currentUserId,
                    createdByName = currentUserName.ifBlank { "Partner" },
                    timestamp = System.currentTimeMillis()
                )

                if (isOnlineMode && coupleId.isNotBlank()) {
                    lifecycleScope.launch {
                        val ok = repository.addCustomTruthOrDareCard(coupleId, newCard)
                        if (ok) {
                            Toast.makeText(requireContext(), "Saved to your Couple Vault! 🔐✨", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Failed to save to cloud, saved locally.", Toast.LENGTH_SHORT).show()
                            customCards.add(0, newCard)
                        }
                    }
                } else {
                    customCards.add(0, newCard)
                    Toast.makeText(requireContext(), "Saved to local deck! 💖", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun triggerHaptic() {
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        syncListener?.remove()
        syncListener = null
        customCardsListener?.remove()
        customCardsListener = null
    }
}
