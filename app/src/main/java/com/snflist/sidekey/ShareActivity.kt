package com.snflist.sidekey

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * The other half of deep linking: share a link into a slot.
 *
 * Only about a dozen apps offer an ACTION_CREATE_SHORTCUT picker, but almost
 * everything has a Share or Copy link - a Spotify playlist, a YouTube video, a
 * Slack channel, a page in a browser. Sharing it here puts that exact link on
 * a key, which covers every app the picker cannot reach.
 */
class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
        val link = firstLink(text)
        if (link == null) {
            Toast.makeText(this, "No link in that", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()
        val label = when {
            !subject.isNullOrBlank() -> subject
            else -> Uri.parse(link).host ?: link
        }

        val prefs = Prefs(this)
        val rows = (1..Slots.COUNT).map { slot ->
            Slots.label(slot) + "  ·  " + (prefs.action(slot)?.summary(this) ?: "not set")
        }

        AlertDialog.Builder(this)
            .setTitle(label.take(40))
            .setItems(rows.toTypedArray()) { _, which ->
                prefs.setAction(which + 1, Action(Kinds.URL, link, label.take(40)))
                Toast.makeText(this, "Put on ${Slots.label(which + 1)}", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    /**
     * Shared text is rarely just a URL - it is usually a title, a newline and
     * then the link - so take the first thing that looks like one.
     */
    private fun firstLink(text: String): String? {
        if (text.isEmpty()) return null
        val token = text.split(Regex("\\s+")).firstOrNull { word ->
            word.contains("://") || word.startsWith("www.")
        } ?: return if (text.contains("://")) text else null
        return if (token.startsWith("www.")) "https://$token" else token
    }
}
