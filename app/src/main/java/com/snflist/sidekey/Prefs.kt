package com.snflist.sidekey

import android.content.Context

/**
 * One action per slot. That is the whole of what this app remembers.
 */
class Prefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("sidekey", Context.MODE_PRIVATE)

    fun action(slot: Int): Action? = Action.parse(prefs.getString("slot.$slot", null))

    fun setAction(slot: Int, action: Action?) {
        prefs.edit().apply {
            if (action == null) remove("slot.$slot") else putString("slot.$slot", action.store())
        }.apply()
    }

    /** Remembers the ringer step so the cycle is predictable. */
    var ringerStep: Int
        get() = prefs.getInt("ringer", 0)
        set(value) = prefs.edit().putInt("ringer", value).apply()
}

/**
 * The bindable "apps".
 *
 * Each slot is an activity-alias in the manifest with its own launcher entry,
 * which is the only thing the phone's shortcut settings will accept. All ten
 * are always present, so all ten are always there to be mapped.
 */
object Slots {

    const val COUNT = 10

    /** The name the phone's own shortcut picker shows for this slot. */
    fun label(slot: Int) = "SideKey $slot"

    /** Which slot an intent landed on, or 0 if it was not a slot at all. */
    fun of(componentClass: String?): Int {
        val name = componentClass ?: return 0
        for (slot in 1..COUNT) if (name == "com.snflist.sidekey.Slot$slot") return slot
        return 0
    }
}
