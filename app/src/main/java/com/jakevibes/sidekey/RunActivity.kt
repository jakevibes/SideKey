package com.jakevibes.sidekey

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * What the side key actually opens.
 *
 * The phone's shortcut settings will only point a key at an app, so every slot
 * is an app: ten activity-aliases of this one invisible activity. It works out
 * which alias was opened, runs whatever is mapped to it, and closes.
 */
class RunActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        run(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        run(intent)
    }

    private fun run(intent: Intent?) {
        val slot = Slots.of(intent?.component?.className)
        if (slot == 0) {
            // Reached some way other than through a slot; nothing to run.
            finish()
            return
        }

        val action = Prefs(this).action(slot)
        if (action == null) {
            Toast.makeText(this, "${Slots.label(slot)} is not set", Toast.LENGTH_SHORT).show()
        } else {
            Act.run(this, action)?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        finishSilently()
    }
}
