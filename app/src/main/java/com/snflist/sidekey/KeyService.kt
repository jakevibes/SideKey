package com.snflist.sidekey

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Back, recents, the shade, the screenshot, the lock.
 *
 * None of those are things an ordinary app may do; an accessibility service is
 * the one supported route, which is why every gesture app asks for the same
 * permission. This one reads nothing: no window content, no key filtering, no
 * event handling at all. It exists purely to call performGlobalAction.
 */
class KeyService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: KeyService? = null

        val isRunning: Boolean get() = instance != null

        /** Runs one of the framework's global actions, or reports it cannot. */
        fun perform(action: Int): Boolean = instance?.performGlobalAction(action) ?: false

        /**
         * Whether the user has switched the service on. Read from Settings
         * rather than from [isRunning] so the settings screen is right even
         * before the service has started.
         */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabled.split(':').any { it.startsWith(context.packageName + "/") }
        }
    }
}
