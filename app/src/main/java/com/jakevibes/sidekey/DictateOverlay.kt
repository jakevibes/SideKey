package com.jakevibes.sidekey

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * A tab that slides out of the right edge while it listens.
 *
 * Dictation happens while you are in someone else's app, so the only place to
 * say anything is on top of it. The right edge is chosen because the text box
 * being dictated into is nearly always at the bottom, and a bar across the top
 * collides with the status bar and every notification.
 *
 * It needs "display over other apps". Without that permission everything still
 * works - the foreground notification is the fallback - so the overlay is
 * simply skipped rather than demanded.
 */
object DictateOverlay {

    private var view: Meter? = null
    private var windows: WindowManager? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(context: Context) {
        if (view != null || !canShow(context)) return
        val manager = context.getSystemService(WindowManager::class.java) ?: return

        val meter = Meter(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        runCatching { manager.addView(meter, params) }
            .onSuccess {
                view = meter
                windows = manager
            }
    }

    /** A microphone level, 0..32767, straight from the recorder. */
    fun level(amplitude: Int) {
        view?.push(amplitude)
    }

    /** Recording has stopped; whisper is now chewing on it. */
    fun thinking() {
        view?.think()
    }

    fun hide() {
        val meter = view ?: return
        meter.stop()
        runCatching { windows?.removeView(meter) }
        view = null
        windows = null
    }

    /**
     * The tab itself: horizontal bars stacked up the right edge, each one as
     * long as the sound was loud, the newest at the bottom.
     */
    private class Meter(context: Context) : View(context) {

        private val density = context.resources.displayMetrics.density
        private val levels = FloatArray(BARS)
        private var sweep = 0f
        private var thinking = false
        private var animator: ValueAnimator? = null

        /**
         * Speech is far quieter than the 16-bit range suggests, and how quiet
         * depends on the room, the phone and how close you hold it. A fixed
         * divisor made every bar sit at the minimum - which is why this looked
         * like a row of dots. The loudest recent sound sets the scale instead,
         * decaying so it follows you rather than latching onto one shout.
         */
        private var peak = QUIETEST

        private val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(240, 20, 10, 12)
        }
        private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED }

        fun push(amplitude: Int) {
            if (thinking) return
            peak = maxOf(amplitude.toFloat(), peak * DECAY).coerceAtLeast(QUIETEST)
            // Square root, because loudness is perceived closer to that than
            // to the raw amplitude; without it everything hugs the bottom.
            val level = kotlin.math.sqrt((amplitude / peak).coerceIn(0f, 1f))
            System.arraycopy(levels, 1, levels, 0, levels.size - 1)
            levels[levels.size - 1] = level
            postInvalidateOnAnimation()
        }

        fun think() {
            if (thinking) return
            thinking = true
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1200
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    sweep = it.animatedValue as Float
                    postInvalidateOnAnimation()
                }
                start()
            }
        }

        fun stop() {
            animator?.cancel()
            animator = null
        }

        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            setMeasuredDimension((26 * density).toInt(), (132 * density).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val radius = 13f * density

            // Rounded on the left only: the right edge is flush with the
            // screen, so it reads as something pulled out of the side.
            val shape = Path().apply {
                addRoundRect(
                    RectF(0f, 0f, w + radius, h),
                    floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius),
                    Path.Direction.CW
                )
            }
            canvas.drawPath(shape, backdrop)

            val inset = 7f * density
            val maxLength = w - inset * 2
            val slot = (h - inset * 2) / BARS
            val thickness = (slot * 0.5f).coerceAtMost(3f * density)

            for (i in levels.indices) {
                val fraction = if (thinking) {
                    val phase = (sweep + i / BARS.toFloat()) % 1f
                    0.3f + 0.7f * kotlin.math.sin(phase * Math.PI).toFloat()
                } else {
                    levels[i]
                }
                val length = (maxLength * fraction).coerceAtLeast(2f * density)
                val cy = inset + slot * i + slot / 2f
                // Grows leftward from the flush edge.
                canvas.drawRoundRect(
                    RectF(w - inset - length, cy - thickness / 2f, w - inset, cy + thickness / 2f),
                    thickness / 2f, thickness / 2f, bar
                )
            }
        }

        companion object {
            const val BARS = 16

            /** Bonfire red, the one accent SideKey draws itself. */
            val RED = Color.parseColor("#FFDA1A32")

            /** The quietest sound that should still fill the meter. */
            const val QUIETEST = 900f

            /** How fast the scale follows you back down. */
            const val DECAY = 0.985f
        }
    }
}
