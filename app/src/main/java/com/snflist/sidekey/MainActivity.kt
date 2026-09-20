package com.snflist.sidekey

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Ten slots. Tap one, say what it does.
 *
 * All the state lives here rather than in a view model: it is ten nullable
 * actions read from SharedPreferences, and a counter that reloads them after
 * an edit. Anything more would be ceremony.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SideKeyTheme {
                SlotsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotsScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    val prefs = remember { Prefs(context) }

    var revision by remember { mutableIntStateOf(0) }
    val actions = remember(revision) { (1..Slots.COUNT).map { prefs.action(it) } }

    // Which slot's editor is open, and which step of it.
    var editing by remember { mutableIntStateOf(0) }
    var choosingApp by remember { mutableIntStateOf(0) }
    var choosingDeepLink by remember { mutableIntStateOf(0) }
    var typing by remember { mutableStateOf<TextRequest?>(null) }

    // The slot waiting on another app to hand back a deep link.
    var pendingSlot by remember { mutableIntStateOf(0) }

    val dictatePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val callPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        context.toast(
            if (granted) "One press will place the call"
            else "The dialer will open with the number in it"
        )
    }

    fun assign(slot: Int, action: Action?) {
        prefs.setAction(slot, action)
        if (action != null && Act.wantsCallPermission(action) && !Act.canCall(context)) {
            callPermission.launch(Manifest.permission.CALL_PHONE)
        }
        if (action?.kind == Kinds.DICTATE) {
            dictatePermissions.launch(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            )
        }
        revision++
    }

    val deepLink = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val slot = pendingSlot
        pendingSlot = 0
        if (result.resultCode != Activity.RESULT_OK || slot == 0) return@rememberLauncherForActivityResult
        val action = Choices.shortcutResult(result.data)
        if (action == null) context.toast("That app did not hand back a link") else assign(slot, action)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SideKey") }) }
    ) { inset ->
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.padding(inset)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Set a slot here, then point a key press at it in the phone's " +
                            "shortcut settings. All ten are listed there as apps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { openShortcutSettings(activity) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
                    ) { Text("Open the phone's shortcut settings") }
                }
            }

            items((1..Slots.COUNT).toList()) { slot ->
                val action = actions[slot - 1]
                ListItem(
                    headlineContent = { Text(Slots.label(slot)) },
                    supportingContent = {
                        Text(
                            action?.summary(context) ?: "not set",
                            color = if (action != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.combinedClickable(
                        onClick = { editing = slot },
                        onLongClick = { action?.let { Act.run(context, it)?.let(context::toast) } }
                    )
                )
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "DICTATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                )
                val haveModel = SpeechModel.installed(context)
                ListItem(
                    headlineContent = {
                        Text(if (haveModel) "Speech model installed" else "Download the speech model")
                    },
                    supportingContent = {
                        Text(
                            if (haveModel) "runs on the phone, nothing is sent anywhere"
                            else "${SpeechModel.MEGABYTES} MB over Wi-Fi, once"
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.combinedClickable(onClick = {
                        if (!haveModel) SpeechModel.download(context)?.let(context::toast)
                        revision++
                    })
                )
                val overlay = DictateOverlay.canShow(context)
                ListItem(
                    headlineContent = {
                        Text(if (overlay) "Listening indicator is on" else "Listening indicator is off")
                    },
                    supportingContent = {
                        Text(
                            if (overlay) "shows a level meter while it listens"
                            else "optional - draws over other apps"
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.combinedClickable(onClick = {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:" + context.packageName)
                            )
                        )
                    })
                )
                val typing = TypeService.isEnabled(context)
                ListItem(
                    headlineContent = {
                        Text(if (typing) "Typing is on" else "Typing is off")
                    },
                    supportingContent = {
                        Text(
                            if (typing) "can write into the focused text box"
                            else "dictation needs this to type what you said"
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.combinedClickable(onClick = {
                        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        context.toast("Find SideKey Dictation in the list")
                    })
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                val on = KeyService.isEnabled(context)
                ListItem(
                    headlineContent = { Text(if (on) "Accessibility is on" else "Accessibility is off") },
                    supportingContent = { Text("needed only for back, shade, screenshot and lock") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.combinedClickable(onClick = {
                        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        context.toast("Find SideKey in the list")
                    })
                )
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Hold a slot to try it. The key itself belongs to a system app " +
                            "and cannot be listened to, which is why this works by being " +
                            "an app rather than by intercepting anything.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // -- the editor, one sheet at a time --------------------------------------

    if (editing != 0) {
        val slot = editing
        ActionSheet(
            current = actions[slot - 1],
            onDismiss = { editing = 0 },
            onPicked = { kind ->
                editing = 0
                when (kind.needs) {
                    Arg.NONE -> assign(slot, Action(kind.id))
                    Arg.APP -> choosingApp = slot
                    Arg.SHORTCUT -> choosingDeepLink = slot
                    else -> typing = TextRequest(slot, kind, actions[slot - 1])
                }
            },
            onClear = {
                editing = 0
                assign(slot, null)
            }
        )
    }

    if (choosingApp != 0) {
        val slot = choosingApp
        AppSheet(
            onDismiss = { choosingApp = 0 },
            onPicked = { component ->
                choosingApp = 0
                assign(slot, Action(Kinds.APP, component))
            }
        )
    }

    if (choosingDeepLink != 0) {
        val slot = choosingDeepLink
        DeepLinkSheet(
            onDismiss = { choosingDeepLink = 0 },
            onPicked = { provider ->
                choosingDeepLink = 0
                pendingSlot = slot
                try {
                    deepLink.launch(
                        Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(provider.component)
                    )
                } catch (e: Exception) {
                    pendingSlot = 0
                    context.toast("That app would not open its picker")
                }
            }
        )
    }

    typing?.let { request ->
        TextDialog(
            request = request,
            onDismiss = { typing = null },
            onDone = { value ->
                typing = null
                assign(request.slot, Action(request.kind.id, value))
            }
        )
    }
}

/**
 * Unihertz keeps the key mapping in its own privileged app rather than in
 * Settings. SelectFunctionActivity opens straight on the Func1 page; its
 * deeper screens finish immediately when started from outside.
 */
private fun openShortcutSettings(activity: Activity) {
    val oem = "com.agui.shortcutsettings"
    val candidates = listOf(
        Intent().setClassName(oem, "$oem.ui.SelectFunctionActivity"),
        Intent().setClassName(oem, "$oem.ui.EntryAppActivity"),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in candidates) {
        try {
            activity.startActivity(intent)
            return
        } catch (e: Exception) {
            continue
        }
    }
    activity.toast("No shortcut settings on this phone")
}

internal fun android.content.Context.toast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
