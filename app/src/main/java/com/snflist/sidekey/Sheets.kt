package com.snflist.sidekey

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** What a text-argument action still needs before it can be stored. */
data class TextRequest(val slot: Int, val kind: Catalogue.Kind, val current: Action?)

/**
 * Everything a slot can be, grouped, with "Nothing" first so a slot can be
 * cleared from the same place it is set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    current: Action?,
    onDismiss: () -> Unit,
    onPicked: (Catalogue.Kind) -> Unit,
    onClear: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.heightIn(max = 560.dp)) {
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
}

/** Every app with a launcher entry, which is the same list a launcher shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val context = LocalContext.current
    val entries = remember { Apps.all(context) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.heightIn(max = 560.dp)) {
            items(entries) { entry ->
                ListItem(
                    headlineContent = { Text(entry.label) },
                    modifier = Modifier.clickable { onPicked(entry.component) }
                )
            }
        }
    }
}

/** The apps that offer a deep link of their own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepLinkSheet(onDismiss: () -> Unit, onPicked: (Choices.Provider) -> Unit) {
    val context = LocalContext.current
    val providers = remember { Choices.shortcutProviders(context) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "OPEN WHAT, IN WHICH APP",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
        )
        if (providers.isEmpty()) {
            ListItem(headlineContent = { Text("No app here offers one") })
            return@ModalBottomSheet
        }
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            items(providers) { provider ->
                ListItem(
                    headlineContent = { Text(provider.label) },
                    modifier = Modifier.clickable { onPicked(provider) }
                )
            }
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
