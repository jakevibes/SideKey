package com.snflist.sidekey

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Putting dictated words into whatever text box is focused.
 *
 * This is the one part of SideKey that has to be able to see the screen, and
 * it is deliberately a *separate* service from [KeyService] so that the choice
 * is yours. KeyService drives back, recents, the shade and screenshot and is
 * content-blind: it cannot read a thing. This one declares
 * canRetrieveWindowContent, which Android will warn you about in the clearest
 * terms, because writing into a field means first finding it.
 *
 * It does nothing on its own: no events are handled, nothing is logged, and
 * the only time it looks at the screen at all is the moment a transcript
 * arrives and it goes looking for the cursor.
 */
class TypeService : AccessibilityService() {

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
        private var instance: TypeService? = null

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabled.split(':').any {
                it.equals("${context.packageName}/${TypeService::class.java.name}", true)
            }
        }

        /**
         * Appends [text] at the cursor, and says whether it landed.
         *
         * ACTION_SET_TEXT replaces a field wholesale, so the existing contents
         * have to be read and put back with the new words on the end. Some
         * fields refuse it - WebViews especially - so the fallback is to put
         * the text on the clipboard and ask the field to paste, which more of
         * them accept.
         */
        fun type(context: Context, text: String): Boolean {
            val service = instance ?: return false
            val focused = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: return false

            if (!focused.isEditable) {
                focused.recycle()
                return false
            }

            val existing = focused.text?.toString().orEmpty()
            val joined = if (existing.isEmpty()) text else "$existing $text"

            val set = focused.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, joined
                    )
                }
            )
            if (set) {
                focused.recycle()
                return true
            }

            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("dictation", text))
            val pasted = focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            focused.recycle()
            return pasted
        }
    }
}
