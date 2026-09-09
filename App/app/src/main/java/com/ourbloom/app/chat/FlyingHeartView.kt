package com.ourbloom.app.chat

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.sin
import kotlin.random.Random

class FlyingHeartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        clipChildren = false
        clipToPadding = false
        isClickable = false
        isFocusable = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    /**
     * Spawns a prominent central heart pop and a burst of floating hearts drifting upwards.
     */
    fun spawnHeartBurst(startX: Float, startY: Float, emoji: String = "❤️") {
        // 1. Big central pop heart
        val bigHeart = TextView(context).apply {
            text = emoji
            textSize = 42f
            alpha = 0f
            x = startX - 50f
            y = startY - 70f
        }
        addView(bigHeart)

        val scaleX = ObjectAnimator.ofFloat(bigHeart, View.SCALE_X, 0.2f, 1.35f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(bigHeart, View.SCALE_Y, 0.2f, 1.35f, 1.0f)
        val alphaIn = ObjectAnimator.ofFloat(bigHeart, View.ALPHA, 0f, 1f)
        val floatUp = ObjectAnimator.ofFloat(bigHeart, View.TRANSLATION_Y, 0f, -80f)
        val fadeOut = ObjectAnimator.ofFloat(bigHeart, View.ALPHA, 1f, 0f).apply {
            startDelay = 450
            duration = 300
        }

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alphaIn, floatUp)
            play(fadeOut).after(450)
            duration = 350
            interpolator = OvershootInterpolator(1.8f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeView(bigHeart)
                }
            })
            start()
        }

        // 2. Small flutter hearts drifting upwards
        val count = 6
        for (i in 0 until count) {
            val smallHeart = TextView(context).apply {
                text = emoji
                textSize = Random.nextFloat() * 10f + 18f
                alpha = 0f
                x = startX - 25f + (Random.nextFloat() - 0.5f) * 60f
                y = startY - 30f + (Random.nextFloat() - 0.5f) * 40f
            }
            addView(smallHeart)

            val driftX = (Random.nextFloat() - 0.5f) * 120f
            val driftY = -Random.nextFloat() * 220f - 120f
            val duration = Random.nextLong(600, 1000)

            val animX = ObjectAnimator.ofFloat(smallHeart, View.TRANSLATION_X, 0f, driftX)
            val animY = ObjectAnimator.ofFloat(smallHeart, View.TRANSLATION_Y, 0f, driftY)
            val animAlpha = ObjectAnimator.ofFloat(smallHeart, View.ALPHA, 0f, 0.9f, 0f)
            val animScale = ObjectAnimator.ofFloat(smallHeart, View.SCALE_X, 0.4f, 1.1f, 0.8f)

            AnimatorSet().apply {
                playTogether(animX, animY, animAlpha, animScale)
                this.duration = duration
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        removeView(smallHeart)
                    }
                })
                start()
            }
        }
    }
}
