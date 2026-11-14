package com.lagradost.cloudstream3.ui.intro

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The app-open animation. It starts on the same frame the system splash (or, before Android 12,
 * `splash_background`) leaves on screen, then plays the logo sequence: red sparks off the play
 * button, a red glow, and the logo settling above the wordmark with a short progress bar.
 *
 * It is an overlay on top of the activity's own content, so the app keeps loading underneath.
 */
object CineVaultIntro {
    private var hasPlayed = false

    /** Plays the intro once per process. Call after the activity has set its content view. */
    fun playOnce(activity: Activity) {
        if (hasPlayed) return
        hasPlayed = true

        // Respect "Remove animations" / animator duration scale 0.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) return

        try {
            val root = activity.window.decorView as? ViewGroup ?: return
            val view = CineVaultIntroView(activity)
            root.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            view.play { root.removeView(view) }
        } catch (t: Throwable) {
            // Never block startup on a cosmetic animation.
            logError(t)
        }
    }
}

@SuppressLint("ViewConstructor")
private class CineVaultIntroView(context: Context) : View(context) {
    private companion object {
        const val TOTAL_MS = 1800f
        const val SKIP_TO_MS = 1500f // start of the fade out
        const val SPARK_COUNT = 22
    }

    private class Spark(
        val angle: Float,
        val distance: Float, // in logo sizes
        val radius: Float, // in dp
        val delay: Float, // ms
    )

    private val logo = ContextCompat.getDrawable(context, R.drawable.ic_cinevault_logo)!!.mutate()
    private val wordmark =
        ContextCompat.getDrawable(context, R.drawable.ic_cinevault_wordmark)!!.mutate()
    private val background = ContextCompat.getColor(context, R.color.ic_launcher_background)
    private val red = ContextCompat.getColor(context, R.color.cinevault_red)
    private val mint = ContextCompat.getColor(context, R.color.cinevault_mint)
    private val density = resources.displayMetrics.density
    private val splashLogoSize = resources.getDimension(R.dimen.cinevault_splash_logo_size)

    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = red }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val decelerate = DecelerateInterpolator(2f)

    // Fixed seed so the burst looks the same every launch.
    private val sparks = Random(7).let { rnd ->
        List(SPARK_COUNT) {
            Spark(
                // Mostly to the right, the way the play button points.
                angle = ((rnd.nextFloat() * 2f - 1f) * 0.45f * PI).toFloat(),
                distance = 0.3f + rnd.nextFloat() * 0.5f,
                radius = 1.8f + rnd.nextFloat() * 2.2f,
                delay = rnd.nextFloat() * 220f,
            )
        }
    }

    private var elapsed = 0f
    private val animator = ValueAnimator.ofFloat(0f, TOTAL_MS).apply {
        duration = TOTAL_MS.toLong()
        interpolator = LinearInterpolator()
        addUpdateListener {
            elapsed = it.animatedValue as Float
            alpha = 1f - progress(SKIP_TO_MS, TOTAL_MS)
            invalidate()
        }
    }

    init {
        // Swallow touches while visible; a tap skips to the fade out.
        isClickable = true
        setOnClickListener {
            if (elapsed < SKIP_TO_MS) animator.currentPlayTime = SKIP_TO_MS.toLong()
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun play(onEnd: () -> Unit) {
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = onEnd()
        })
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.removeAllListeners()
        animator.cancel()
        super.onDetachedFromWindow()
    }

    /** 0..1 progress of [elapsed] through the window [start]..[end] (ms). */
    private fun progress(start: Float, end: Float) =
        ((elapsed - start) / (end - start)).coerceIn(0f, 1f)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun withAlpha(color: Int, alpha: Float) =
        ColorUtils.setAlphaComponent(color, (alpha.coerceIn(0f, 1f) * 255).toInt())

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(background)

        val cx = width / 2f
        val cy = height / 2f
        // Same size as the system splash so the first frame lines up; smaller on tiny windows.
        val base = min(splashLogoSize, min(width, height) * 0.45f)

        // Final layout (panel 4): smaller logo, wordmark, progress bar, centred as a group.
        val finalScale = 0.7f
        val wordmarkHeight = base * 0.2f
        val wordmarkWidth =
            wordmarkHeight * wordmark.intrinsicWidth / wordmark.intrinsicHeight.coerceAtLeast(1)
        val gap = base * 0.12f
        val barHeight = 3f * density
        val groupHeight = base * finalScale + gap + wordmarkHeight + gap + barHeight
        val finalLogoCy = cy - groupHeight / 2f + base * finalScale / 2f

        // 1. Sparks (0-650 ms) and 2. glow (200-1000 ms), 3. settle (700-1100 ms).
        val settle = decelerate.getInterpolation(progress(700f, 1100f))
        val pulse = 1f + 0.04f * sin(PI * progress(250f, 650f)).toFloat()
        val size = base * lerp(1f, finalScale, settle) * pulse
        val logoCy = lerp(cy, finalLogoCy, settle)
        val left = cx - size / 2f
        val top = logoCy - size / 2f

        // The play button's centre in the emblem: 81% across, 49% down.
        val playX = left + size * 0.81f
        val playY = top + size * 0.49f

        // The glow sits on the play button: a red wash over the whole green emblem turns muddy.
        val glow = 0.9f * progress(200f, 550f) - 0.6f * progress(550f, 1000f)
        if (glow > 0f) {
            val r = size * 0.5f
            glowPaint.shader = RadialGradient(
                playX, playY, r,
                intArrayOf(withAlpha(red, glow), withAlpha(red, glow * 0.35f), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(playX, playY, r, glowPaint)
        }

        for (spark in sparks) {
            val t = progress(spark.delay, spark.delay + 430f)
            if (t <= 0f || t >= 1f) continue
            val d = decelerate.getInterpolation(t) * spark.distance * size
            sparkPaint.alpha = ((1f - t) * 255).toInt()
            canvas.drawCircle(
                playX + cos(spark.angle) * d,
                playY + sin(spark.angle) * d,
                spark.radius * density * (1f - 0.5f * t),
                sparkPaint
            )
        }

        logo.setBounds(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
        logo.draw(canvas)

        // 4. Wordmark and progress bar (850-1500 ms).
        val textIn = progress(850f, 1150f)
        if (textIn > 0f) {
            val rise = (1f - decelerate.getInterpolation(textIn)) * 12f * density
            val wmTop = finalLogoCy + base * finalScale / 2f + gap + rise
            val wmLeft = cx - wordmarkWidth / 2f
            wordmark.alpha = (textIn * 255).toInt()
            wordmark.setBounds(
                wmLeft.toInt(),
                wmTop.toInt(),
                (wmLeft + wordmarkWidth).toInt(),
                (wmTop + wordmarkHeight).toInt()
            )
            wordmark.draw(canvas)

            val barWidth = wordmarkWidth * 0.55f
            val barLeft = cx - barWidth / 2f
            val barTop = wmTop + wordmarkHeight + gap
            val radius = barHeight / 2f
            barPaint.color = withAlpha(mint, 0.18f * textIn)
            canvas.drawRoundRect(
                barLeft, barTop, barLeft + barWidth, barTop + barHeight, radius, radius, barPaint
            )
            val fill = decelerate.getInterpolation(progress(950f, 1500f))
            if (fill > 0f) {
                barPaint.color = withAlpha(mint, 0.85f * textIn)
                canvas.drawRoundRect(
                    barLeft, barTop, barLeft + barWidth * fill, barTop + barHeight,
                    radius, radius, barPaint
                )
            }
        }
    }
}
