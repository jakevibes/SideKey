package com.snflist.sidekey

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * The lists the picker offers, and the one awkward round trip.
 *
 * Kept out of the Compose layer deliberately: none of this is UI, and querying
 * PackageManager from inside a composable would run it on every recomposition.
 */
object Choices {

    data class Provider(val label: String, val component: ComponentName)

    /**
     * Apps that offer a deep link of their own.
     *
     * An app with anything worth linking to declares an activity for
     * ACTION_CREATE_SHORTCUT - WhatsApp a contact picker, Contacts a direct
     * dial, Maps a set of directions, Settings any of its pages. Opening it
     * hands back a ready-made intent, so nobody has to know what a deep link
     * looks like.
     */
    fun shortcutProviders(context: Context): List<Provider> {
        val pm = context.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_CREATE_SHORTCUT), 0)
            .map { info ->
                val app = info.activityInfo.applicationInfo.loadLabel(pm).toString()
                val what = info.loadLabel(pm).toString()
                Provider(
                    if (what.startsWith(app, ignoreCase = true)) what else "$app: $what",
                    ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * What the app handed back: an intent to fire, and its own name for it.
     * Null when the app was cancelled or answered with something unusable.
     */
    @Suppress("DEPRECATION") // EXTRA_SHORTCUT_* are deprecated but are still
    // exactly what an ACTION_CREATE_SHORTCUT activity answers with.
    fun shortcutResult(data: Intent?): Action? {
        if (data == null) return null
        val intent = if (Build.VERSION.SDK_INT >= 33) {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
        } else {
            data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)
        } ?: return null
        val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME).orEmpty()
        return Action(Kinds.SHORTCUT, intent.toUri(Intent.URI_INTENT_SCHEME), name)
    }
}
