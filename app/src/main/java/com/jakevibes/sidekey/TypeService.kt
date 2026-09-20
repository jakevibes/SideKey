package com.jakevibes.sidekey

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
         * Puts [text] at the cursor, and says whether it landed.
         *
         * Pasting first, deliberately. The obvious approach - read the field,
         * append, write it back with ACTION_SET_TEXT - has a trap in it:
         * getText() returns the *hint* when a field is empty, so an empty
         * WhatsApp box reads back as "Message" and you get that word in front
         * of everything you say. There are flags meant to tell you that is
         * happening and they are not reliable across apps.
         *
         * ACTION_PASTE never reads the field at all. It inserts at the cursor,
         * which is also the behaviour you want when adding to a half-written
         * message. SET_TEXT stays as the fallback for fields that refuse to
         * paste, and there the hint is guarded against as carefully as it can
         * be.
         */
        fun type(context: Context, text: String): Boolean {
            val service = instance ?: return false
            val focused = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            if (!focused.isEditable) {
                focused.recycle()
                return false
            }

            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val previous = clipboard?.primaryClip
            clipboard?.setPrimaryClip(ClipData.newPlainText("Dictation", text))

            if (focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                focused.recycle()
                // Give the paste a moment, then hand the clipboard back rather
                // than leaving the transcript sitting in it.
                previous?.let { old ->
                    android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed({ runCatching { clipboard.setPrimaryClip(old) } }, 800)
                }
                return true
            }

            val shown = focused.text?.toString().orEmpty()
            val hint = focused.hintText?.toString().orEmpty()
            val existing = when {
                focused.isShowingHintText -> ""
                hint.isNotEmpty() && shown == hint -> ""
                else -> shown
            }
            val joined = if (existing.isEmpty()) text else "$existing $text"

            val set = focused.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, joined
                    )
                }
            )
            focused.recycle()
            return set
        }
    }
}
