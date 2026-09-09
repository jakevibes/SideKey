package com.snflist.sidekey

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Doing the thing.
 *
 * Every action returns a message worth showing, or null for "it just worked
 * and you can see that it did". Nothing here is asynchronous except the
 * webhook, which reports back on its own.
 */
object Act {

    fun run(context: Context, action: Action): String? = try {
        dispatch(context, action)
    } catch (e: Exception) {
        e.message ?: "that did not work"
    }

    private fun dispatch(context: Context, action: Action): String? = when (action.kind) {
        Kinds.APP -> launchApp(context, action.arg)
        Kinds.URL -> start(context, Intent(Intent.ACTION_VIEW, webUri(action.arg)))
        Kinds.SEARCH -> start(
            context,
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, action.arg)
        )
        Kinds.CAMERA -> start(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        Kinds.ASSISTANT -> start(context, Intent(Intent.ACTION_VOICE_COMMAND))
        Kinds.INTERNET -> start(
            context,
            if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            else Intent(Settings.ACTION_WIFI_SETTINGS)
        )

        Kinds.TORCH -> torch(context)
        Kinds.RINGER -> ringer(context)
        Kinds.DND -> dnd(context)
        Kinds.TIMER -> timer(context, action.arg)
        Kinds.ALARM -> alarm(context, action.arg)

        Kinds.MEDIA_TOGGLE -> media(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        Kinds.MEDIA_NEXT -> media(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        Kinds.MEDIA_PREV -> media(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

        Kinds.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK)
        Kinds.HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME)
        Kinds.RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
        Kinds.NOTIFICATIONS -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        Kinds.QUICK_SETTINGS -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        Kinds.POWER -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
        Kinds.LOCK ->
            if (Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            else "this Android is too old to lock the screen this way"
        Kinds.SCREENSHOT ->
            if (Build.VERSION.SDK_INT >= 30) global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            else "this Android is too old to screenshot this way"

        Kinds.DIAL -> call(context, Uri.parse("tel:" + action.arg.trim()))
        Kinds.SMS -> start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + action.arg.trim())))

        Kinds.WEBHOOK_GET -> webhook(context, action.arg, "GET")
        Kinds.WEBHOOK_POST -> webhook(context, action.arg, "POST")
        Kinds.SHORTCUT,
        Kinds.INTENT -> {
            val intent = Intent.parseUri(action.arg, Intent.URI_INTENT_SCHEME)
            // A "direct dial" shortcut comes back as ACTION_CALL.
            if (intent.action == Intent.ACTION_CALL) call(context, intent.data)
            else start(context, intent)
        }
        Kinds.BROADCAST -> {
            context.sendBroadcast(Intent.parseUri(action.arg, Intent.URI_INTENT_SCHEME))
            null
        }

        else -> "no idea what \"${action.kind}\" is"
    }

    // -- the pieces ----------------------------------------------------------

    private fun start(context: Context, intent: Intent): String? = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (e: SecurityException) {
        "that needs a permission SideKey does not have"
    } catch (e: Exception) {
        "nothing on this phone handles that"
    }

    /**
     * One press, one call. ACTION_CALL dials immediately and needs CALL_PHONE
     * to do it; without the permission this falls back to opening the dialer
     * with the number filled in, which is the same press and one more tap.
     */
    private fun call(context: Context, number: Uri?): String? {
        if (number == null) return "no number in that shortcut"
        return if (canCall(context)) {
            start(context, Intent(Intent.ACTION_CALL, number))
        } else {
            start(context, Intent(Intent.ACTION_DIAL, number))
                ?: "allow SideKey to make calls for one-press dialling"
        }
    }

    /** Whether one-press dialling is available, for the settings screen too. */
    fun canCall(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** True for anything that will place a call when the key is pressed. */
    fun wantsCallPermission(action: Action): Boolean = when (action.kind) {
        Kinds.DIAL -> true
        Kinds.SHORTCUT, Kinds.INTENT -> try {
            Intent.parseUri(action.arg, Intent.URI_INTENT_SCHEME).action == Intent.ACTION_CALL
        } catch (e: Exception) {
            false
        }
        else -> false
    }

    private fun launchApp(context: Context, flat: String): String? {
        val component = Apps.component(flat) ?: return "that shortcut is broken"
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            "that app is gone"
        }
    }

    /**
     * "example.com" is a web address; "example.com:8080" still is, but
     * "intent:", "tel:" and "myapp:" are not. A scheme is only believed when
     * what precedes the colon looks like a scheme and not like a hostname,
     * which in practice means it has no dot in it.
     */
    private fun webUri(raw: String): Uri {
        val text = raw.trim()
        val colon = text.indexOf(':')
        val scheme = if (colon > 0) text.substring(0, colon) else ""
        val hasScheme = scheme.isNotEmpty() &&
            !scheme.contains('.') &&
            scheme.matches(Regex("[a-zA-Z][a-zA-Z0-9+-]*"))
        return Uri.parse(if (hasScheme) text else "https://$text")
    }

    private fun global(action: Int): String? =
        if (KeyService.perform(action)) null
        else "turn on SideKey in Settings > Accessibility first"

    private fun media(context: Context, keyCode: Int): String? {
        val audio = context.getSystemService(AudioManager::class.java) ?: return "no audio service"
        val now = android.os.SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        return null
    }

    /**
     * The torch, read before it is written - without blocking to do it.
     *
     * A remembered boolean goes wrong the moment anything else touches the
     * flash, so the real state is asked for. But registerTorchCallback answers
     * on its own schedule, and on a cold start - which is nearly every press,
     * since this activity finishes at once and the process is then reclaimed -
     * that takes longer than it is reasonable to sit and wait for. Waiting a
     * few hundred milliseconds and then guessing "off" is worse than useless:
     * it turns the torch on again when you meant to turn it off.
     *
     * So the toggle happens inside the callback, and the remembered value is
     * only the backstop for an answer that never comes.
     */
    private fun torch(context: Context): String? {
        val manager = context.getSystemService(CameraManager::class.java) ?: return "no camera service"
        val id = manager.cameraIdList.firstOrNull { camera ->
            manager.getCameraCharacteristics(camera)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "this phone has no torch"

        val prefs = Prefs(context.applicationContext)
        val thread = HandlerThread("torch").apply { start() }
        val handler = Handler(thread.looper)
        val settled = AtomicBoolean(false)
        var callback: CameraManager.TorchCallback? = null

        fun toggle(currentlyOn: Boolean) {
            if (!settled.compareAndSet(false, true)) return
            val next = !currentlyOn
            try {
                manager.setTorchMode(id, next)
                prefs.torchOn = next
            } catch (e: Exception) {
                // The flash can be held by the camera app; nothing to do.
            }
            callback?.let { runCatching { manager.unregisterTorchCallback(it) } }
            thread.quitSafely()
        }

        callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) toggle(enabled)
            }
        }
        manager.registerTorchCallback(callback, handler)
        handler.postDelayed({ toggle(prefs.torchOn) }, 1500)
        return null
    }

    private fun ringer(context: Context): String? {
        val audio = context.getSystemService(AudioManager::class.java) ?: return "no audio service"
        val notifications = context.getSystemService(NotificationManager::class.java)
        val next = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        // Going silent counts as changing the interruption policy.
        if (next == AudioManager.RINGER_MODE_SILENT && notifications?.isNotificationPolicyAccessGranted != true) {
            start(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return "allow SideKey to change Do Not Disturb, then try again"
        }
        audio.ringerMode = next
        return when (next) {
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            AudioManager.RINGER_MODE_SILENT -> "silent"
            else -> "ringer on"
        }
    }

    private fun dnd(context: Context): String? {
        val notifications = context.getSystemService(NotificationManager::class.java)
            ?: return "no notification service"
        if (!notifications.isNotificationPolicyAccessGranted) {
            start(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return "allow SideKey to change Do Not Disturb, then try again"
        }
        val off = notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        notifications.setInterruptionFilter(
            if (off) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return if (off) "do not disturb on" else "do not disturb off"
    }

    private fun timer(context: Context, minutes: String): String? {
        val length = minutes.trim().toIntOrNull() ?: return "set the number of minutes first"
        return start(
            context,
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, length * 60)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "SideKey")
        ) ?: "$length min"
    }

    private fun alarm(context: Context, time: String): String? {
        val parts = time.trim().split(':', '.')
        val hour = parts.getOrNull(0)?.toIntOrNull()
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        if (hour == null || hour !in 0..23 || minute !in 0..59) return "write the time as 07:30"
        return start(
            context,
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "SideKey")
        )
    }

    /**
     * Fire and mostly forget. The reply is only reported so a broken URL is
     * not silent; nothing is done with the body.
     */
    private fun webhook(context: Context, url: String, method: String): String? {
        val target = webUri(url).toString()
        val app = context.applicationContext
        Thread {
            val message = try {
                val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 8000
                    readTimeout = 8000
                    if (method == "POST") {
                        doOutput = true
                        outputStream.close()
                    }
                }
                val code = connection.responseCode
                connection.inputStream.use { it.bufferedReader().use(BufferedReader::readText) }
                connection.disconnect()
                if (code in 200..299) null else "webhook: HTTP $code"
            } catch (e: Exception) {
                "webhook failed: ${e.message}"
            }
            if (message != null) {
                Handler(Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(app, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
        return null
    }
}
