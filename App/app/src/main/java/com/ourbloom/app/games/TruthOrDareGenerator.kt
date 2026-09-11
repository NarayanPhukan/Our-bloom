package com.ourbloom.app.games

import com.ourbloom.app.games.models.TruthOrDareCard
import java.util.UUID
import kotlin.random.Random

/**
 * Procedural Endless Generator for Truth or Dare.
 * Generates thousands of distinct, romantic, playful, deep, and teasing prompts on the fly.
 */
object TruthOrDareGenerator {

    private val dareSpicyActions = listOf(
        "Give your partner a slow, breathtaking kiss on their",
        "Gently trace your fingertips and breathe warmly against your partner's",
        "Place 3 soft, lingering kisses along your partner's",
        "Slowly massage and tease your partner's",
        "Sensually nibble and softly kiss your partner's",
        "Take an ice cube (or warm fingertips) and glide it slowly down your partner's",
        "Close your eyes and let your partner guide your hands over their",
        "Whisper your most forbidden, seductive desire against your partner's",
        "Run your lips tenderly across your partner's",
        "Sit facing your partner, wrap your hands around their",
        "Gaze into your partner's eyes for 30 seconds while tenderly caressing their",
        "Plant a trail of gentle kisses from their jaw down to their",
        "Hold your partner firmly by the waist and softly bite their"
    )

    private val dareSpicyTargets = listOf(
        "neck and jawline",
        "collarbone and bare shoulders",
        "lower back and hips",
        "sensitive earlobe and nape of the neck",
        "inner wrist and forearm",
        "lips, pulling them close",
        "bare waistline and stomach",
        "thighs with a slow, teasing touch"
    )

    private val dareSpicyModifiers = listOf(
        "for a full unbroken 45 seconds without breaking contact.",
        "in total darkness or with only a candle burning.",
        "while whispering what you love most about their body.",
        "without speaking a single word, purely with touch.",
        "taking your sweet time with every single breath.",
        "while maintaining deep eye contact until they blush.",
        "(If apart/online: record a 20-second sensual video/voice note whispering this promise)."
    )

    private val darePlayfulActions = listOf(
        "Do a dramatic, over-the-top salsa or slow dance with your partner for 1 minute!",
        "Feed your partner a sweet treat or fruit using only your lips!",
        "Give your partner a 60-second royalty treatment where you must answer every request with 'Yes, my love'!",
        "Let your partner blindfold you and feed you 2 mystery items from the kitchen to guess!",
        "Re-enact your favorite movie's romantic confession scene in a silly dramatic accent!",
        "Let your partner style your hair into the funniest, cutest hairstyle they can think of!",
        "Show your partner the last photo on your phone camera roll and explain the full story!",
        "Speak in playful whispers right in your partner's ear for the next 3 rounds of this game!"
    )

    private val dareRomanticActions = listOf(
        "Hold both of your partner's hands over your heart and recite 3 reasons why you chose them.",
        "Put on our favorite romantic song and slow-dance together right where you are.",
        "Give your partner an uninterrupted 2-minute foot or shoulder massage.",
        "Write a 4-line rhyming love poem for your partner on a napkin or piece of paper right now.",
        "Gaze into your partner's eyes for 60 seconds in silence, then seal it with a deep kiss."
    )

    private val truthSpicyPrompts = listOf(
        "What is the most intense, seductive fantasy or dream you've had about me recently?",
        "Where is your most sensitive erogenous spot that gives you shivers when I kiss or touch it?",
        "What is one outfit, look, or perfume/scent of mine that turns you on immediately?",
        "What is something naughty or adventurous you've secretly wanted to try together in bed?",
        "Describe your absolute favorite kiss we've ever shared — what made it so unforgettable?",
        "If you could have me do any romantic or teasing dare right now, what would you ask for?",
        "What goes through your mind when you look at me across the room when we're around others?",
        "Do you prefer slow, deeply emotional intimacy, or spontaneous, intense, breathtaking passion?",
        "What is your biggest instant turn-on when it comes to my voice, touch, or body language?",
        "What is one dirty thought about me that caught you by surprise during an ordinary day?",
        "If we booked a private luxury cabin for an entire weekend with zero clothes allowed, how would we spend it?",
        "What's the boldest place in public or travel where you've ever thought about stealing a kiss or touch?",
        "What is your favorite type of kiss: slow and gentle, deep and hungry, or teasing neck kisses?",
        "What is one touch or move of mine that instantly melts all your resistance?"
    )

    private val truthDeepPrompts = listOf(
        "When did you realize that you didn't just love me, but couldn't imagine your life without me?",
        "What is a fear or vulnerability about the future that you feel safe sharing with me?",
        "In what subtle ways do you feel like our love has transformed or healed you?",
        "What is a memory of us that always brings a genuine smile to your face on hard days?",
        "What does true emotional safety feel like to you in our relationship?",
        "What is one dream or life goal you want us to accomplish together in the next 5 years?",
        "If you could relive any 24 hours of our relationship from the very start, which day would you pick?"
    )

    private val truthPlayfulPrompts = listOf(
        "What was the very first thought you had about me when you saw me or met me?",
        "What is the funniest or most absurd reason you've ever gotten pouted or grumpy with me?",
        "If you had to describe our relationship using only 3 emojis, which ones and why?",
        "What is a quirky, cute habit of mine that nobody else in the world notices?",
        "Who would survive longer on a deserted island: you or me, and what would happen on day one?"
    )

    private val truthSweetPrompts = listOf(
        "What is your favorite small thing I do for you that makes you feel appreciated?",
        "What is a song that instantly makes you think of holding hands with me?",
        "What was the sweetest compliment I ever gave you that you still hold in your heart?",
        "What is one photo of us that is your absolute favorite, and what happened when it was taken?",
        "What do you love most about how we comfort each other when one of us is tired or stressed?"
    )

    /**
     * Procedurally builds a unique Truth or Dare card.
     */
    fun generateCard(
        preferredType: String?,
        category: String
    ): TruthOrDareCard {
        val type = preferredType ?: if (Random.nextBoolean()) "TRUTH" else "DARE"
        val cat = if (category == "ALL" || category == "ENDLESS") {
            listOf("SPICY", "SWEET", "PLAYFUL", "DEEP").random()
        } else {
            category
        }

        val prompt = if (type == "TRUTH") {
            generateTruth(cat)
        } else {
            generateDare(cat)
        }

        return TruthOrDareCard(
            id = "gen_${UUID.randomUUID().toString().take(8)}",
            type = type,
            category = cat,
            prompt = prompt
        )
    }

    private fun generateTruth(category: String): String {
        return when (category) {
            "SPICY" -> truthSpicyPrompts.random()
            "DEEP" -> truthDeepPrompts.random()
            "PLAYFUL" -> truthPlayfulPrompts.random()
            "SWEET" -> truthSweetPrompts.random()
            else -> (truthSpicyPrompts + truthDeepPrompts + truthSweetPrompts + truthPlayfulPrompts).random()
        }
    }

    private fun generateDare(category: String): String {
        return when (category) {
            "SPICY" -> {
                val action = dareSpicyActions.random()
                val target = dareSpicyTargets.random()
                val mod = dareSpicyModifiers.random()
                "$action $target $mod 🔥"
            }
            "PLAYFUL" -> darePlayfulActions.random()
            "SWEET", "DEEP" -> dareRomanticActions.random()
            else -> {
                if (Random.nextBoolean()) {
                    val action = dareSpicyActions.random()
                    val target = dareSpicyTargets.random()
                    val mod = dareSpicyModifiers.random()
                    "$action $target $mod 🔥"
                } else {
                    (darePlayfulActions + dareRomanticActions).random()
                }
            }
        }
    }
}
