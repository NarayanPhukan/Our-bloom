package com.ourbloom.app.games

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.ourbloom.app.R
import com.ourbloom.app.games.models.TruthOrDareCard
import kotlin.random.Random

class TruthOrDareFragment : Fragment() {

    private lateinit var tvTodBadge: TextView
    private lateinit var tvTodCategoryBadge: TextView
    private lateinit var tvTodText: TextView
    private lateinit var cardTodPrompt: MaterialCardView
    private lateinit var chipGroupCategory: ChipGroup

    private var selectedCategory: String = "ALL"
    private var lastDrawnIndex: Int = -1

    private val prompts = listOf(
        // SWEET (TRUTH)
        TruthOrDareCard("s_t_1", "TRUTH", "SWEET", "When was the exact moment you realized you had fallen deeply in love with me? ♡"),
        TruthOrDareCard("s_t_2", "TRUTH", "SWEET", "What is your favorite memory of us from our very first month together?"),
        TruthOrDareCard("s_t_3", "TRUTH", "SWEET", "What is the sweetest thing I've ever done that completely melted your heart?"),
        TruthOrDareCard("s_t_4", "TRUTH", "SWEET", "What is a small, quiet habit of mine that you secretly find irresistibly adorable?"),
        TruthOrDareCard("s_t_5", "TRUTH", "SWEET", "If you could freeze one single moment in time with me forever, which one would it be?"),
        // SWEET (DARE)
        TruthOrDareCard("s_d_1", "DARE", "SWEET", "Look into your partner's eyes for 45 seconds without laughing, then give them a warm tight hug! 🌸"),
        TruthOrDareCard("s_d_2", "DARE", "SWEET", "Kiss your partner on the forehead, both cheeks, and their lips, whispering one compliment for each kiss ♡"),
        TruthOrDareCard("s_d_3", "DARE", "SWEET", "Send your partner a sweet 20-second voice note right now saying why they are your favorite person in the universe."),
        TruthOrDareCard("s_d_4", "DARE", "SWEET", "Slow dance together to an imaginary romantic song for 1 minute right where you are! 💃🕺"),

        // DEEP (TRUTH)
        TruthOrDareCard("d_t_1", "TRUTH", "DEEP", "What makes you feel most safe, cherished, and deeply loved in our relationship?"),
        TruthOrDareCard("d_t_2", "TRUTH", "DEEP", "What is one dream or aspiration about our future together that you haven't shared yet?"),
        TruthOrDareCard("d_t_3", "TRUTH", "DEEP", "In what ways do you feel like we have helped each other grow into better versions of ourselves?"),
        TruthOrDareCard("d_t_4", "TRUTH", "DEEP", "What is a song, place, or scent that will always remind you of our love story?"),
        TruthOrDareCard("d_t_5", "TRUTH", "DEEP", "What is something vulnerable you wanted to tell me lately but hadn't found the right moment for?"),
        // DEEP (DARE)
        TruthOrDareCard("d_d_1", "DARE", "DEEP", "Write down 3 promises for our future on a note and place it in their hands to read aloud."),
        TruthOrDareCard("d_d_2", "DARE", "DEEP", "Hold both of your partner's hands over your heart and tell them your favorite thing about their soul."),

        // PLAYFUL (TRUTH)
        TruthOrDareCard("p_t_1", "TRUTH", "PLAYFUL", "What is the funniest or most awkward thought that crossed your mind on our first date? 😂"),
        TruthOrDareCard("p_t_2", "TRUTH", "PLAYFUL", "Who is the bigger dramatic baby when they catch a cold or don't get food on time?"),
        TruthOrDareCard("p_t_3", "TRUTH", "PLAYFUL", "If our love story was a cheesy romantic comedy movie, what ridiculous title would it have?"),
        // PLAYFUL (DARE)
        TruthOrDareCard("p_d_1", "DARE", "PLAYFUL", "Call your partner only by silly royalty titles ('Your Highness', 'My Liege') for the next 10 minutes! 👑"),
        TruthOrDareCard("p_d_2", "DARE", "PLAYFUL", "Do your best, most dramatic impression of how your partner acts when they are sleepy or cranky!"),
        TruthOrDareCard("p_d_3", "DARE", "PLAYFUL", "Sing the chorus of our favorite song in the most exaggerated opera voice possible! 🎶"),
        TruthOrDareCard("p_d_4", "DARE", "PLAYFUL", "Let your partner style your hair however they want and take a cute selfie together!"),

        // SPICY & INTIMATE (TRUTH)
        TruthOrDareCard("sp_t_1", "TRUTH", "SPICY", "What is your absolute favorite part of my body to kiss, touch, and caress? 🔥"),
        TruthOrDareCard("sp_t_2", "TRUTH", "SPICY", "What is a secret romantic or sexual fantasy you have about me that you haven't shared yet?"),
        TruthOrDareCard("sp_t_3", "TRUTH", "SPICY", "What is the hottest dream or daydream you've ever had about us being intimate together?"),
        TruthOrDareCard("sp_t_4", "TRUTH", "SPICY", "What is something I do in bed or while kissing that drives you completely wild?"),
        TruthOrDareCard("sp_t_5", "TRUTH", "SPICY", "What kind of outfit, sleepwear, or lingerie would drive you completely crazy if I wore it for you?"),
        TruthOrDareCard("sp_t_6", "TRUTH", "SPICY", "Where is your most sensitive erogenous zone that makes you shiver when I touch or kiss it?"),
        TruthOrDareCard("sp_t_7", "TRUTH", "SPICY", "Do you prefer slow, deeply emotional intimacy, or spontaneous, intense, breathless passion?"),
        TruthOrDareCard("sp_t_8", "TRUTH", "SPICY", "If we had the whole house to ourselves with no clothes allowed for a whole weekend, what would we do first?"),
        TruthOrDareCard("sp_t_9", "TRUTH", "SPICY", "What is your biggest instant turn-on that makes you desire me immediately?"),
        TruthOrDareCard("sp_t_10", "TRUTH", "SPICY", "Describe our most passionate time being intimate together in delicious detail."),
        TruthOrDareCard("sp_t_11", "TRUTH", "SPICY", "What dirty or naughty thought about me crossed your mind recently when you looked at me?"),
        TruthOrDareCard("sp_t_12", "TRUTH", "SPICY", "Where is the most daring or adventurous place you'd ever want to make love with me?"),

        // SPICY & INTIMATE (DARE)
        TruthOrDareCard("sp_d_1", "DARE", "SPICY", "Give your partner a slow, passionate 60-second French kiss with your hands tangled in their hair or waist. 🔥"),
        TruthOrDareCard("sp_d_2", "DARE", "SPICY", "Slowly remove one item of your partner's clothing using only your fingertips or teeth with a seductive smile."),
        TruthOrDareCard("sp_d_3", "DARE", "SPICY", "Close your eyes (or wear a blindfold) and let your partner kiss 3 sensitive intimate spots; guess each one!"),
        TruthOrDareCard("sp_d_4", "DARE", "SPICY", "Spend 60 seconds planting soft, warm kisses on your partner's neck, jawline, and earlobes without touching their lips."),
        TruthOrDareCard("sp_d_5", "DARE", "SPICY", "Whisper into your partner's ear the exact naughty, passionate thing you want to do to them tonight in bed."),
        TruthOrDareCard("sp_d_6", "DARE", "SPICY", "Bite your partner's bottom lip gently, pull them tightly by the waist, and tell them what you find irresistible about them."),
        TruthOrDareCard("sp_d_7", "DARE", "SPICY", "Sit on your partner's lap facing them, look deep into their eyes, and share a 30-second breathless kiss."),
        TruthOrDareCard("sp_d_8", "DARE", "SPICY", "Give your partner a sensual 2-minute massage on their lower back and inner thighs with warm hands."),
        TruthOrDareCard("sp_d_9", "DARE", "SPICY", "Trace your fingertips slowly down your partner's chest and stomach, leaving a soft kiss trail behind."),
        TruthOrDareCard("sp_d_10", "DARE", "SPICY", "Take off one piece of your own clothing of your partner's choice with a slow, teasing smile."),
        TruthOrDareCard("sp_d_11", "DARE", "SPICY", "Let your partner leave a gentle love mark or soft kiss trail anywhere on your neck, shoulder, or chest.")
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

        tvTodBadge = view.findViewById(R.id.tv_tod_badge)
        tvTodCategoryBadge = view.findViewById(R.id.tv_tod_category_badge)
        tvTodText = view.findViewById(R.id.tv_tod_text)
        cardTodPrompt = view.findViewById(R.id.card_tod_prompt)
        chipGroupCategory = view.findViewById(R.id.chip_group_tod_category)

        view.findViewById<ImageButton>(R.id.btn_back_tod).setOnClickListener {
            findNavController().navigateUp()
        }

        chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedCategory = when (checkedIds.firstOrNull()) {
                R.id.chip_cat_sweet -> "SWEET"
                R.id.chip_cat_deep -> "DEEP"
                R.id.chip_cat_playful -> "PLAYFUL"
                R.id.chip_cat_spicy -> "SPICY"
                else -> "ALL"
            }
        }

        view.findViewById<MaterialButton>(R.id.btn_pick_truth).setOnClickListener {
            drawPrompt(preferredType = "TRUTH")
        }

        view.findViewById<MaterialButton>(R.id.btn_pick_dare).setOnClickListener {
            drawPrompt(preferredType = "DARE")
        }

        view.findViewById<MaterialButton>(R.id.btn_random_spin).setOnClickListener {
            drawPrompt(preferredType = null)
        }

        cardTodPrompt.setOnClickListener {
            drawPrompt(preferredType = null)
        }
    }

    private fun drawPrompt(preferredType: String?) {
        triggerHaptic()

        val filtered = prompts.filter { card ->
            val matchCategory = selectedCategory == "ALL" || card.category == selectedCategory
            val matchType = preferredType == null || card.type == preferredType
            matchCategory && matchType
        }

        if (filtered.isEmpty()) {
            tvTodText.text = "No cards found in this category. Try selecting All Decks! ✨"
            return
        }

        var nextIndex = Random.nextInt(filtered.size)
        if (filtered.size > 1 && nextIndex == lastDrawnIndex) {
            nextIndex = (nextIndex + 1) % filtered.size
        }
        lastDrawnIndex = nextIndex

        val chosenCard = filtered[nextIndex]
        animateCardFlip(chosenCard)
    }

    private fun animateCardFlip(card: TruthOrDareCard) {
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
                    else -> "✨ COUPLE DECK"
                }

                cardTodPrompt.rotationY = -90f
                cardTodPrompt.animate()
                    .rotationY(0f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun triggerHaptic() {
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}
    }
}
