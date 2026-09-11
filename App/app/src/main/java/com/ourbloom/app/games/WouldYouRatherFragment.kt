package com.ourbloom.app.games

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
import com.ourbloom.app.R
import com.ourbloom.app.games.models.WouldYouRatherQuestion

class WouldYouRatherFragment : Fragment() {

    private lateinit var tvProgress: TextView
    private lateinit var cardOptionA: MaterialCardView
    private lateinit var tvOptionAText: TextView
    private lateinit var cardOptionB: MaterialCardView
    private lateinit var tvOptionBText: TextView
    private lateinit var cardResult: MaterialCardView
    private lateinit var tvResultText: TextView
    private lateinit var btnNext: MaterialButton

    private var currentIndex = 0
    private var selectedOption: String? = null

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
        WouldYouRatherQuestion("wyr_15", "A 30-minute foot & back massage from your partner 💆", "Your partner doing all house chores for an entire week 🧹")
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

        tvProgress = view.findViewById(R.id.tv_wyr_progress)
        cardOptionA = view.findViewById(R.id.card_option_a)
        tvOptionAText = view.findViewById(R.id.tv_option_a_text)
        cardOptionB = view.findViewById(R.id.card_option_b)
        tvOptionBText = view.findViewById(R.id.tv_option_b_text)
        cardResult = view.findViewById(R.id.card_wyr_result)
        tvResultText = view.findViewById(R.id.tv_wyr_result_text)
        btnNext = view.findViewById(R.id.btn_next_wyr)

        view.findViewById<ImageButton>(R.id.btn_back_wyr).setOnClickListener {
            findNavController().navigateUp()
        }

        cardOptionA.setOnClickListener {
            selectOption("A")
        }

        cardOptionB.setOnClickListener {
            selectOption("B")
        }

        btnNext.setOnClickListener {
            nextQuestion()
        }

        renderQuestion()
    }

    private fun renderQuestion() {
        val q = questions[currentIndex]
        tvProgress.text = "Dilemma ${currentIndex + 1} of ${questions.size} • Romantic Sync"
        tvOptionAText.text = q.optionA
        tvOptionBText.text = q.optionB

        selectedOption = null
        resetCardSelectionUI()
        cardResult.visibility = View.GONE
    }

    private fun selectOption(option: String) {
        triggerHaptic()
        selectedOption = option

        if (option == "A") {
            cardOptionA.strokeColor = 0xFF1976D2.toInt()
            cardOptionA.strokeWidth = 4
            cardOptionA.setCardBackgroundColor(0xFFE3F2FD.toInt())

            cardOptionB.strokeColor = 0xFFE0E0E0.toInt()
            cardOptionB.strokeWidth = 1
            cardOptionB.setCardBackgroundColor(0xFFF5F5F5.toInt())

            tvResultText.text = "You picked Option A! 💖 Ask your partner what they would choose!"
        } else {
            cardOptionB.strokeColor = 0xFFE85D75.toInt()
            cardOptionB.strokeWidth = 4
            cardOptionB.setCardBackgroundColor(0xFFFFEBEE.toInt())

            cardOptionA.strokeColor = 0xFFE0E0E0.toInt()
            cardOptionA.strokeWidth = 1
            cardOptionA.setCardBackgroundColor(0xFFF5F5F5.toInt())

            tvResultText.text = "You picked Option B! 💖 Ask your partner what they would choose!"
        }

        cardResult.visibility = View.VISIBLE
    }

    private fun resetCardSelectionUI() {
        cardOptionA.strokeColor = 0xFFBBDEFB.toInt()
        cardOptionA.strokeWidth = 2
        cardOptionA.setCardBackgroundColor(0xFFFFFFFF.toInt())

        cardOptionB.strokeColor = 0xFFFFCDD2.toInt()
        cardOptionB.strokeWidth = 2
        cardOptionB.setCardBackgroundColor(0xFFFFFFFF.toInt())
    }

    private fun nextQuestion() {
        triggerHaptic()
        currentIndex = (currentIndex + 1) % questions.size
        renderQuestion()
    }

    private fun triggerHaptic() {
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}
    }
}
