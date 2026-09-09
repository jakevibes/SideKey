package com.snflist.sidekey

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** What a text-argument action still needs before it can be stored. */
data class TextRequest(val slot: Int, val kind: Catalogue.Kind, val current: Action?)

/**
 * A picker that owns the whole screen.
 *
 * This phone is 360x400dp. A bottom sheet leaves so little room above itself
 * that there is barely any scrim to tap, and dragging inside it fights with
 * scrolling the list underneath - which made the sheet feel stuck. Taking the
 * screen instead gives the list all the room and puts an unmissable Close in
 * the corner. Back still works, as it does for any dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(title: String, onDismiss: () -> Unit, content: LazyListScope.() -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        actions = { TextButton(onClick = onDismiss) { Text("Close") } }
                    )
                }
            ) { inset ->
                LazyColumn(Modifier.padding(inset).fillMaxSize(), content = content)
            }
        }
    }
}

/**
 * Everything a slot can be, grouped, with "Nothing" first so a slot can be
 * cleared from the same place it is set.
 */
@Composable
fun ActionSheet(
    current: Action?,
    onDismiss: () -> Unit,
    onPicked: (Catalogue.Kind) -> Unit,
    onClear: () -> Unit
) {
    Picker("What should it do?", onDismiss) {
        item {
            ListItem(
                headlineContent = { Text("Nothing") },
                supportingContent = if (current == null) {
                    { Text("currently set") }
                } else null,
                modifier = Modifier.clickable { onClear() }
            )
        }
        var group = ""
        Catalogue.ALL.forEach { kind ->
            if (kind.group != group) {
                group = kind.group
                item {
                    Text(
                        kind.group.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(kind.title) },
                    supportingContent = when {
                        kind.system -> {
                            { Text("needs the accessibility switch") }
                        }
                        kind.id == current?.kind -> {
                            { Text("currently set") }
                        }
                        else -> null
                    },
                    modifier = Modifier.clickable { onPicked(kind) }
                )
            }
        }
    }
}

/** Every app with a launcher entry, which is the same list a launcher shows. */
@Composable
fun AppSheet(onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val context = LocalContext.current
    val entries = remember { Apps.all(context) }
    Picker("Open which app?", onDismiss) {
        items(entries) { entry ->
            ListItem(
                headlineContent = { Text(entry.label) },
                modifier = Modifier.clickable { onPicked(entry.component) }
            )
        }
    }
}

/** The apps that offer a deep link of their own. */
@Composable
fun DeepLinkSheet(onDismiss: () -> Unit, onPicked: (Choices.Provider) -> Unit) {
    val context = LocalContext.current
    val providers = remember { Choices.shortcutProviders(context) }
    Picker("Open what, in which app?", onDismiss) {
        if (providers.isEmpty()) {
            item { ListItem(headlineContent = { Text("No app here offers one") }) }
        }
        items(providers) { provider ->
            ListItem(
                headlineContent = { Text(provider.label) },
                modifier = Modifier.clickable { onPicked(provider) }
            )
        }
    }
}

/** The one thing an action still needs: a number, a URL, an intent URI. */
@Composable
fun TextDialog(request: TextRequest, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val previous = if (request.current?.kind == request.kind.id) request.current.arg else ""
    var value by remember { mutableStateOf(previous) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(request.kind.title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text(request.kind.hint) },
                    singleLine = request.kind.needs != Arg.INTENT,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (request.kind.needs) {
                            Arg.NUMBER -> KeyboardType.Number
                            Arg.PHONE -> KeyboardType.Phone
                            Arg.URL -> KeyboardType.Uri
                            else -> KeyboardType.Text
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onDone(value.trim()) },
                enabled = value.isNotBlank()
            ) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
