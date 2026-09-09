package com.snflist.sidekey

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Ten slots. Tap one, say what it does.
 *
 * Rebuilt from scratch in onResume rather than kept in sync, because a redraw
 * of a list this short is cheaper to reason about than a diff.
 */
class MainActivity : Activity() {

    private lateinit var column: LinearLayout

    /**
     * Which slot is waiting on an app to hand back a deep link. Saved, because
     * the app we send you to can push this activity out of memory.
     */
    private var pendingSlot = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        column = Ui.column(this)
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column)
        })
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    private fun rebuild() {
        val prefs = Prefs(this)
        column.removeAllViews()

        column.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.fg))
            setPadding(Ui.dp(this@MainActivity, 12f), Ui.dp(this@MainActivity, 16f), 0, 0)
        })
        column.addView(
            Ui.body(
                this,
                "Set a slot here, then point a key press at it in the phone's " +
                    "shortcut settings. All ten are listed there as apps."
            )
        )
        column.addView(Ui.button(this, "Open the phone's shortcut settings").apply {
            setOnClickListener { openShortcutSettings() }
        })

        for (slot in 1..Slots.COUNT) {
            val action = prefs.action(slot)
            column.addView(
                Ui.row(
                    this,
                    Slots.label(slot),
                    action?.summary(this) ?: "not set",
                    accentSubtitle = action != null
                ).apply {
                    setOnClickListener {
                        pendingSlot = slot
                        Picker.action(this@MainActivity, action) { picked ->
                            pendingSlot = 0
                            prefs.setAction(slot, picked)
                            picked?.let { askForCallPermission(it) }
                            rebuild()
                        }
                    }
                    setOnLongClickListener {
                        action?.let { Act.run(this@MainActivity, it)?.let(::toast) }
                        true
                    }
                }
            )
        }

        column.addView(Ui.header(this, "System actions"))
        column.addView(
            Ui.row(
                this,
                if (KeyService.isEnabled(this)) "Accessibility is on" else "Accessibility is off",
                "needed only for back, shade, screenshot and lock",
                accentSubtitle = KeyService.isEnabled(this)
            ).apply {
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    toast("Find SideKey in the list")
                }
            }
        )
        column.addView(
            Ui.body(
                this,
                "Hold a slot to try it. The key itself belongs to a system app " +
                    "and cannot be listened to, which is why this works by being " +
                    "an app rather than by intercepting anything."
            )
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pendingSlot", pendingSlot)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        pendingSlot = savedInstanceState.getInt("pendingSlot", 0)
    }

    /** The deep link an app just handed back, landing in the slot that asked. */
    @Deprecated("startActivityForResult is the only route CREATE_SHORTCUT offers")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != Picker.REQUEST_SHORTCUT) return
        val slot = pendingSlot
        pendingSlot = 0
        if (resultCode != RESULT_OK || slot == 0) return

        val action = Picker.shortcutResult(data)
        if (action == null) {
            toast("That app did not hand back a link")
            return
        }
        Prefs(this).setAction(slot, action)
        askForCallPermission(action)
        rebuild()
    }

    /**
     * Asked when the slot is set, not when the key is pressed: an invisible
     * activity firing a permission dialog under your thumb would be worse than
     * the one extra tap. Refusing is fine - the dialer opens instead.
     */
    private fun askForCallPermission(action: Action) {
        if (!Act.wantsCallPermission(action) || Act.canCall(this)) return
        requestPermissions(arrayOf(android.Manifest.permission.CALL_PHONE), REQUEST_CALL)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CALL) return
        toast(
            if (Act.canCall(this)) "One press will place the call"
            else "The dialer will open with the number in it"
        )
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    /**
     * Unihertz keeps the key mapping in its own privileged app rather than in
     * Settings. SelectFunctionActivity opens straight on the Func1 page; its
     * deeper screens finish immediately when started from outside.
     */
    private fun openShortcutSettings() {
        val candidates = listOf(
            Intent().setClassName(OEM, "$OEM.ui.SelectFunctionActivity"),
            Intent().setClassName(OEM, "$OEM.ui.EntryAppActivity"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                continue
            }
        }
        toast("No shortcut settings on this phone")
    }

    private companion object {
        /** The privileged app that owns the Titan's keys. */
        const val OEM = "com.agui.shortcutsettings"
        const val REQUEST_CALL = 42
    }
}
