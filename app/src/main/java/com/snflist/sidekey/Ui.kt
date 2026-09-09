package com.snflist.sidekey

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The whole visual vocabulary, in one file.
 *
 * Views are built in code rather than inflated, because there are only five
 * shapes here and an XML layout per screen would be more to keep in step than
 * it is worth. Sizes assume the Titan's short 400dp-tall screen, where
 * vertical space is the thing in short supply.
 */
object Ui {

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
        ).toInt()

    fun column(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** A section heading: small, dim, spaced out. */
    fun header(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text.uppercase()
        textSize = 11f
        letterSpacing = 0.14f
        setTextColor(context.getColor(R.color.faint))
        setPadding(dp(context, 12f), dp(context, 18f), dp(context, 12f), dp(context, 6f))
    }

    /** Running prose, for the one paragraph of explanation the app needs. */
    fun body(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 13f
        setLineSpacing(dp(context, 3f).toFloat(), 1f)
        setTextColor(context.getColor(R.color.dim))
        setPadding(dp(context, 12f), dp(context, 2f), dp(context, 12f), dp(context, 8f))
    }

    /**
     * A tappable line: what it is, and underneath, what it currently does.
     * The subtitle carries the state, so the row is readable at a glance
     * without opening it.
     */
    fun row(context: Context, title: String, subtitle: String?, accentSubtitle: Boolean = false): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setBackgroundResource(R.drawable.row)
            setPadding(dp(context, 12f), dp(context, 9f), dp(context, 12f), dp(context, 9f))
            addView(TextView(context).apply {
                this.text = title
                textSize = 15f
                setTextColor(context.getColor(R.color.fg))
            })
            if (subtitle != null) {
                addView(TextView(context).apply {
                    this.text = subtitle
                    textSize = 12f
                    setTextColor(
                        context.getColor(if (accentSubtitle) R.color.accent else R.color.dim)
                    )
                })
            }
        }

    fun button(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 14f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(context.getColor(R.color.accent))
        setBackgroundResource(R.drawable.button)
        setPadding(dp(context, 16f), dp(context, 10f), dp(context, 16f), dp(context, 10f))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dp(context, 12f)
            rightMargin = dp(context, 12f)
            topMargin = dp(context, 4f)
            bottomMargin = dp(context, 4f)
        }
    }

    fun divider(context: Context): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1f)
        ).apply {
            leftMargin = dp(context, 12f)
            rightMargin = dp(context, 12f)
            topMargin = dp(context, 6f)
        }
        setBackgroundColor(context.getColor(R.color.faint))
    }
}
