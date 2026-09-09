package com.snflist.sidekey

import android.app.Activity
import android.os.Build

/**
 * Finish without the system animating anything.
 *
 * Both slot and menu activities are meant to feel like the key did the thing
 * directly, and a close animation on an invisible window is a quarter second
 * of nothing. overridePendingTransition was deprecated in Android 14, so the
 * old call is kept only for phones that still need it.
 */
fun Activity.finishSilently() {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
    finish()
}
