package com.snflist.sidekey

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * One thing the key can do: a kind, plus at most one argument.
 *
 * Deliberately flat. Every action is a single line of text in
 * SharedPreferences, which keeps the settings screen, the menu and the slots
 * all reading from the same shape.
 */
data class Action(
    val kind: String,
    val arg: String = "",
    /**
     * What to call it, when the argument is not readable. A deep link picked
     * out of an app arrives with the app's own name for it - "Chat with Mum" -
     * and an intent URI nobody wants to look at.
     */
    val label: String = ""
) {

    fun store(): String = kind + SEP + arg + SEP + label

    /** What this action is called, once the argument is known. */
    fun summary(context: Context): String {
        val title = Catalogue.byId(kind)?.title ?: kind
        return when {
            label.isNotBlank() -> label
            arg.isBlank() -> title
            kind == Kinds.APP -> Apps.labelOf(context, arg) ?: title
            kind == Kinds.TIMER -> "Timer, $arg min"
            kind == Kinds.ALARM -> "Alarm at $arg"
            else -> "$title: ${arg.take(40)}"
        }
    }

    companion object {
        /** Unit separator: never legal inside a component name or a URL. */
        private val SEP = 31.toChar()

        fun parse(line: String?): Action? {
            if (line.isNullOrBlank()) return null
            val parts = line.split(SEP)
            val kind = parts[0]
            if (Catalogue.byId(kind) == null) return null
            return Action(kind, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" })
        }
    }
}

/** What sort of argument a kind needs, which is also what the editor shows. */
enum class Arg { NONE, APP, SHORTCUT, TEXT, URL, NUMBER, TIME, PHONE, INTENT }

object Kinds {
    const val APP = "app"
    const val SHORTCUT = "shortcut"
    const val DICTATE = "dictate"
    const val URL = "url"
    const val SEARCH = "search"
    const val INTENT = "intent"
    const val BROADCAST = "broadcast"
    const val TORCH = "torch"
    const val MEDIA_TOGGLE = "media_toggle"
    const val MEDIA_NEXT = "media_next"
    const val MEDIA_PREV = "media_prev"
    const val RINGER = "ringer"
    const val DND = "dnd"
    const val BACK = "back"
    const val HOME = "home"
    const val RECENTS = "recents"
    const val NOTIFICATIONS = "notifications"
    const val QUICK_SETTINGS = "quick_settings"
    const val LOCK = "lock"
    const val SCREENSHOT = "screenshot"
    const val POWER = "power"
    const val TIMER = "timer"
    const val ALARM = "alarm"
    const val CAMERA = "camera"
    const val ASSISTANT = "assistant"
    const val DIAL = "dial"
    const val SMS = "sms"
    const val WEBHOOK_GET = "webhook_get"
    const val WEBHOOK_POST = "webhook_post"
    const val INTERNET = "internet"
}

/**
 * Everything the key can be pointed at, in the order the picker shows it.
 *
 * `group` is only a heading; `needs` decides which second step the editor
 * offers. Adding an action is one entry here plus one branch in [Act].
 */
object Catalogue {

    data class Kind(
        val id: String,
        val group: String,
        val title: String,
        val needs: Arg = Arg.NONE,
        /** Placeholder shown in the argument box. */
        val hint: String = "",
        /** True when it goes through the accessibility service. */
        val system: Boolean = false
    )

    val ALL: List<Kind> = listOf(
        Kind(Kinds.APP, "Open", "Open an app", Arg.APP),
        Kind(Kinds.SHORTCUT, "Open", "Open something inside an app", Arg.SHORTCUT),
        Kind(Kinds.URL, "Open", "Open a link or deep link", Arg.URL, "https://… or spotify:…"),
        Kind(Kinds.SEARCH, "Open", "Web search", Arg.TEXT, "what to search for"),
        Kind(Kinds.CAMERA, "Open", "Camera"),
        Kind(Kinds.ASSISTANT, "Open", "Voice assistant"),
        Kind(Kinds.INTERNET, "Open", "Wi-Fi and data panel"),

        Kind(Kinds.DICTATE, "Device", "Dictate: press to start, press to stop"),
        Kind(Kinds.TORCH, "Device", "Torch"),
        Kind(Kinds.RINGER, "Device", "Cycle ringer: loud, vibrate, silent"),
        Kind(Kinds.DND, "Device", "Toggle do not disturb"),
        Kind(Kinds.TIMER, "Device", "Start a timer", Arg.NUMBER, "minutes"),
        Kind(Kinds.ALARM, "Device", "Set an alarm", Arg.TIME, "07:30"),

        Kind(Kinds.MEDIA_TOGGLE, "Music", "Play or pause"),
        Kind(Kinds.MEDIA_NEXT, "Music", "Next track"),
        Kind(Kinds.MEDIA_PREV, "Music", "Previous track"),

        Kind(Kinds.BACK, "System", "Back", system = true),
        Kind(Kinds.HOME, "System", "Home", system = true),
        Kind(Kinds.RECENTS, "System", "Recent apps", system = true),
        Kind(Kinds.NOTIFICATIONS, "System", "Notification shade", system = true),
        Kind(Kinds.QUICK_SETTINGS, "System", "Quick settings", system = true),
        Kind(Kinds.LOCK, "System", "Lock the screen", system = true),
        Kind(Kinds.SCREENSHOT, "System", "Screenshot", system = true),
        Kind(Kinds.POWER, "System", "Power menu", system = true),

        Kind(Kinds.DIAL, "People", "Call a number", Arg.PHONE, "+1 555 0100"),
        Kind(Kinds.SMS, "People", "Message a number", Arg.PHONE, "+1 555 0100"),

        Kind(Kinds.WEBHOOK_GET, "Advanced", "Webhook (GET)", Arg.URL, "https://"),
        Kind(Kinds.WEBHOOK_POST, "Advanced", "Webhook (POST)", Arg.URL, "https://"),
        Kind(Kinds.INTENT, "Advanced", "Custom intent", Arg.INTENT, "intent:#Intent;action=...;end"),
        Kind(Kinds.BROADCAST, "Advanced", "Send a broadcast", Arg.INTENT, "intent:#Intent;action=...;end")
    )

    private val index = ALL.associateBy { it.id }

    fun byId(id: String): Kind? = index[id]
}

/** The installed apps, read the same way a launcher reads them. */
object Apps {

    data class Entry(val label: String, val component: String)

    fun all(context: Context): List<Entry> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        return pm.queryIntentActivities(query, 0)
            .map { info ->
                Entry(
                    info.loadLabel(pm).toString(),
                    "${info.activityInfo.packageName}/${info.activityInfo.name}"
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun component(flat: String): ComponentName? {
        val at = flat.indexOf('/')
        if (at <= 0) return null
        return ComponentName(flat.substring(0, at), flat.substring(at + 1))
    }

    /** The app's own name, or null if it has been uninstalled since. */
    fun labelOf(context: Context, flat: String): String? = try {
        val component = component(flat) ?: return null
        val pm = context.packageManager
        pm.getActivityInfo(component, 0).loadLabel(pm).toString()
    } catch (e: Exception) {
        null
    }
}
