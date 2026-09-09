package com.snflist.sidekey

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView

/**
 * Choosing what a key does: pick a kind, then fill in the one thing it needs.
 *
 * Two steps rather than one long list, because "open an app" times the number
 * of installed apps is not a menu anybody wants to read.
 */
object Picker {

    /** The CREATE_SHORTCUT round trip comes back through onActivityResult. */
    const val REQUEST_SHORTCUT = 41


    /**
     * Runs [onPicked] with a complete action, or with null to clear the slot.
     * Does nothing if the dialog is dismissed.
     */
    fun action(activity: Activity, current: Action?, onPicked: (Action?) -> Unit) {
        val column = Ui.column(activity)
        val dialog = AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(column) })
            .create()

        column.addView(
            Ui.row(activity, "Nothing", if (current == null) "currently set" else null).apply {
                setOnClickListener {
                    dialog.dismiss()
                    onPicked(null)
                }
            }
        )

        var group = ""
        Catalogue.ALL.forEach { kind ->
            if (kind.group != group) {
                group = kind.group
                column.addView(Ui.header(activity, group))
            }
            val note = when {
                kind.system -> "needs the accessibility switch"
                kind.id == current?.kind -> "currently set"
                else -> null
            }
            column.addView(
                Ui.row(activity, kind.title, note, accentSubtitle = kind.id == current?.kind).apply {
                    setOnClickListener {
                        dialog.dismiss()
                        argument(activity, kind, current) { onPicked(it) }
                    }
                }
            )
        }
        dialog.show()
    }

    private fun argument(
        activity: Activity,
        kind: Catalogue.Kind,
        current: Action?,
        onPicked: (Action) -> Unit
    ) {
        val previous = if (current?.kind == kind.id) current.arg else ""
        when (kind.needs) {
            Arg.NONE -> onPicked(Action(kind.id))
            Arg.APP -> app(activity) { onPicked(Action(kind.id, it)) }
            Arg.SHORTCUT -> shortcut(activity)
            else -> text(activity, kind, previous) { onPicked(Action(kind.id, it)) }
        }
    }

    /** Every app with a launcher entry, which is the same list a launcher shows. */
    fun app(activity: Activity, onPicked: (String) -> Unit) {
        val entries = Apps.all(activity)
        val list = ListView(activity).apply {
            adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_list_item_1,
                entries.map { it.label }
            )
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Open which app")
            .setView(list)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            onPicked(entries[position].component)
        }
        dialog.show()
    }

    /**
     * Deep links, picked out of the app that owns them.
     *
     * An app that has anything worth linking to declares an activity for
     * ACTION_CREATE_SHORTCUT - WhatsApp offers a contact picker, Contacts a
     * direct dial, Maps a set of directions, Settings any of its pages. We
     * open that activity and it hands back a ready-made intent, so nobody has
     * to know what a deep link looks like.
     *
     * The answer arrives in onActivityResult, not here, so this returns
     * nothing and [shortcutResult] finishes the job.
     */
    private fun shortcut(activity: Activity) {
        val pm = activity.packageManager
        val entries = pm.queryIntentActivities(Intent(Intent.ACTION_CREATE_SHORTCUT), 0)
            .map { info ->
                val app = info.activityInfo.applicationInfo.loadLabel(pm).toString()
                val what = info.loadLabel(pm).toString()
                val name = if (what.startsWith(app, ignoreCase = true)) what else "$app: $what"
                name to ComponentName(info.activityInfo.packageName, info.activityInfo.name)
            }
            .sortedBy { it.first.lowercase() }

        if (entries.isEmpty()) {
            android.widget.Toast.makeText(
                activity, "No app here offers one", android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val list = ListView(activity).apply {
            adapter = ArrayAdapter(
                activity, android.R.layout.simple_list_item_1, entries.map { it.first }
            )
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Open what, in which app")
            .setView(list)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            try {
                activity.startActivityForResult(
                    Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(entries[position].second),
                    REQUEST_SHORTCUT
                )
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    activity, "That app would not open its picker", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        dialog.show()
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
            @Suppress("DEPRECATION")
            data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)
        } ?: return null
        val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME).orEmpty()
        return Action(Kinds.SHORTCUT, intent.toUri(Intent.URI_INTENT_SCHEME), name)
    }

    private fun text(
        activity: Activity,
        kind: Catalogue.Kind,
        previous: String,
        onPicked: (String) -> Unit
    ) {
        val field = EditText(activity).apply {
            hint = kind.hint
            setText(previous)
            setSelection(previous.length)
            inputType = when (kind.needs) {
                Arg.NUMBER -> InputType.TYPE_CLASS_NUMBER
                Arg.PHONE -> InputType.TYPE_CLASS_PHONE
                Arg.URL -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                Arg.INTENT -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                else -> InputType.TYPE_CLASS_TEXT
            }
        }
        val padding = Ui.dp(activity, 20f)
        AlertDialog.Builder(activity)
            .setTitle(kind.title)
            .setView(LinearLayout(activity).apply {
                setPadding(padding, Ui.dp(activity, 8f), padding, 0)
                addView(field)
            })
            .setPositiveButton("Set") { _, _ ->
                val value = field.text.toString().trim()
                if (value.isNotEmpty()) onPicked(value)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
