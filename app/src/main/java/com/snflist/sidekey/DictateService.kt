package com.snflist.sidekey

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast

/**
 * Dictation that outlives the key press.
 *
 * A press opens [RunActivity], which finishes immediately - so nothing about a
 * press can hold a recording open. This service does: the first press starts
 * it, the second stops it, transcribes, and hands the words to [TypeService].
 *
 * It is a foreground service because it holds the microphone while you are
 * looking at a different app, which Android requires to be visible. The
 * notification is the point rather than an annoyance: something recording you
 * in the background should be impossible to miss.
 */
class DictateService : Service() {

    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finishUp()
            else -> begin()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        if (Voice.isRecording) return
        startForeground(NOTIFICATION, notification())

        DictateOverlay.show(this)

        // The room going quiet is a safety net; a second press is the usual end.
        val problem = Voice.start(
            context = this,
            onLevel = { level -> main.post { DictateOverlay.level(level) } },
            onSilence = { main.post { finishUp() } }
        )
        if (problem != null) {
            toast(problem)
            DictateOverlay.hide()
            stop()
        }
    }

    /**
     * Stop, transcribe, type. Transcription is seconds of CPU, so it happens
     * off the main thread and the service stays up until it is done.
     */
    private fun finishUp() {
        if (!Voice.isRecording && !listening) {
            stop()
            return
        }
        listening = false
        Voice.stop()
        DictateOverlay.thinking()

        Thread {
            val text = Voice.transcribe(applicationContext)
            main.post {
                when {
                    text.isNullOrBlank() -> toast("Nothing heard")
                    TypeService.type(applicationContext, text) -> Unit
                    else -> {
                        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            android.content.ClipData.newPlainText("Dictation", text)
                        )
                        toast("No text box focused - copied instead")
                    }
                }
                DictateOverlay.hide()
                stop()
            }
        }.start()
    }

    private fun stop() {
        DictateOverlay.hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun toast(message: String) =
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && manager?.getNotificationChannel(CHANNEL) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Dictation", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntentFor(this, Intent(this, DictateService::class.java).setAction(ACTION_STOP))
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Listening")
            .setContentText("Press the key again to stop")
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "dictation"
        private const val NOTIFICATION = 1
        const val ACTION_STOP = "com.snflist.sidekey.STOP_DICTATION"

        /** Set while a recording is being started, before Voice reports it. */
        @Volatile
        private var listening = false

        /** One press starts it, the next ends it. */
        fun toggle(context: Context): String? {
            if (!Voice.isReady(context)) return "Install the speech model in SideKey first"
            if (!TypeService.isEnabled(context)) {
                return "Turn on SideKey Dictation in Settings > Accessibility"
            }

            val intent = Intent(context, DictateService::class.java)
            return if (Voice.isRecording || listening) {
                context.startForegroundService(intent.setAction(ACTION_STOP))
                null
            } else {
                listening = true
                context.startForegroundService(intent)
                null
            }
        }
    }
}

/** Notification actions need a PendingIntent; this is the only one we make. */
@Suppress("FunctionName")
private fun PendingIntentFor(context: Context, intent: Intent) =
    android.app.PendingIntent.getService(
        context, 0, intent,
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
    )
