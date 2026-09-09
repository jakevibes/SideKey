package com.snflist.sidekey

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The other half of deep linking: share a link into a slot.
 *
 * Only about a dozen apps offer an ACTION_CREATE_SHORTCUT picker, but almost
 * everything has a Share or Copy link - a Spotify playlist, a YouTube video, a
 * Slack channel, a page in a browser. Sharing it here puts that exact link on
 * a key, which covers every app the picker cannot reach.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shared = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
        val link = firstLink(shared)
        if (link == null) {
            toast("No link in that")
            finish()
            return
        }

        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()
        val label = (if (!subject.isNullOrBlank()) subject else Uri.parse(link).host ?: link).take(40)
        val prefs = Prefs(this)

        setContent {
            SideKeyTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(label) },
                    text = {
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items((1..Slots.COUNT).toList()) { slot ->
                                ListItem(
                                    headlineContent = { Text(Slots.label(slot)) },
                                    supportingContent = {
                                        Text(prefs.action(slot)?.summary(this@ShareActivity) ?: "not set")
                                    },
                                    modifier = Modifier.clickable {
                                        prefs.setAction(slot, Action(Kinds.URL, link, label))
                                        toast("Put on ${Slots.label(slot)}")
                                        finish()
                                    }
                                )
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { finish() }) { Text("Cancel") } }
                )
            }
        }
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
