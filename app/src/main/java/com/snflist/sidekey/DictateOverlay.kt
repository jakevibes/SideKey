package com.snflist.sidekey

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Something to look at while it listens.
 *
 * Dictation happens while you are in someone else's app, so the only place to
 * say anything is on top of it. This is a small pill at the top of the screen:
 * live bars driven by the microphone while recording, a slow sweep while
 * whisper is thinking, then gone.
 *
 * It needs "display over other apps". Without that permission everything still
 * works - the foreground notification is the fallback - so the overlay is
 * simply skipped rather than demanded.
 */
object DictateOverlay {

    private var view: Wave? = null
    private var windows: WindowManager? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(context: Context) {
        if (view != null || !canShow(context)) return
        val manager = context.getSystemService(WindowManager::class.java) ?: return

        val wave = Wave(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            // Top, because the text box being dictated into is nearly always
            // at the bottom and covering it would be perverse.
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (24 * context.resources.displayMetrics.density).toInt()
        }

        runCatching { manager.addView(wave, params) }
            .onSuccess {
                view = wave
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
        val wave = view ?: return
        wave.stop()
        runCatching { windows?.removeView(wave) }
        view = null
        windows = null
    }

    /**
     * The pill itself. A plain View rather than anything from Compose: it has
     * to be addable from a service, it draws twelve rectangles, and it should
     * cost nothing while the phone is busy transcribing.
     */
    private class Wave(context: Context) : View(context) {

        private val density = context.resources.displayMetrics.density
        private val bars = FloatArray(BAR_COUNT)
        private var sweep = 0f
        private var thinking = false
        private var animator: ValueAnimator? = null

        private val accent = if (Build.VERSION.SDK_INT >= 31) {
            context.getColor(android.R.color.system_accent1_300)
        } else {
            Color.parseColor("#C0564A")
        }

        private val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 16, 16, 20)
        }
        private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13f * density
            typeface = Typeface.DEFAULT_BOLD
        }

        fun push(amplitude: Int) {
            if (thinking) return
            // Speech sits well below full scale, so this is scaled to what a
            // voice actually produces rather than to the 16-bit range.
            val level = (amplitude / 4000f).coerceIn(0.05f, 1f)
            System.arraycopy(bars, 1, bars, 0, bars.size - 1)
            bars[bars.size - 1] = level
            postInvalidateOnAnimation()
        }

        fun think() {
            if (thinking) return
            thinking = true
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1100
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
            setMeasuredDimension((190 * density).toInt(), (44 * density).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val h = height.toFloat()
            val r = h / 2f
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), h), r, r, backdrop)

            val text = if (thinking) "Transcribing" else "Listening"
            canvas.drawText(text, 16f * density, h / 2f + 5f * density, label)

            val left = 104f * density
            val slot = 6f * density
            val barWidth = 3f * density
            val maxBar = 20f * density

            for (i in bars.indices) {
                val fraction = if (thinking) {
                    // A slow wave running through the bars, so it is obviously
                    // still working rather than frozen.
                    val phase = (sweep + i / BAR_COUNT.toFloat()) % 1f
                    0.25f + 0.75f * kotlin.math.sin(phase * Math.PI).toFloat()
                } else {
                    bars[i]
                }
                val barHeight = (maxBar * fraction).coerceAtLeast(2f * density)
                val x = left + i * slot
                canvas.drawRoundRect(
                    RectF(x, (h - barHeight) / 2f, x + barWidth, (h + barHeight) / 2f),
                    barWidth / 2f, barWidth / 2f, bar
                )
            }
        }

        companion object {
            const val BAR_COUNT = 12
        }
    }
}
