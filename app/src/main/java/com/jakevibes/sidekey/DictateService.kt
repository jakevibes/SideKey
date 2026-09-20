package com.jakevibes.sidekey

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
        val auto = Prefs(this).autoStop
        startForeground(NOTIFICATION, notification(auto))
        DictateOverlay.show(this)

        val problem = Voice.start(
            context = this,
            onLevel = { level -> main.post { DictateOverlay.level(level) } },
            stopOnSilence = auto,
            maxSeconds = if (auto) Voice.MAX_SECONDS else Voice.MAX_MANUAL_SECONDS,
            onEnded = { main.post { finishUp() } }
        )
        if (problem != null) {
            toast(problem)
            stage = Stage.IDLE
            stop()
        }
    }

    /**
     * Stop, transcribe, type. Transcription is seconds of CPU, so it happens
     * off the main thread and the service stays up until it is done.
     */
    private fun finishUp() {
        // Reached from the key press and from the recorder thread ending; only
        // the first of them should transcribe.
        if (stage != Stage.RECORDING) return
        stage = Stage.TRANSCRIBING
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
                stage = Stage.IDLE
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

    private fun notification(auto: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && manager?.getNotificationChannel(CHANNEL) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Dictation", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntentFor(this, Intent(this, DictateService::class.java).setAction(ACTION_STOP))
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Listening")
            .setContentText(
                if (auto) "Stops when you pause, or press the key again"
                else "Press the key again to stop"
            )
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "dictation"
        private const val NOTIFICATION = 1
        const val ACTION_STOP = "com.jakevibes.sidekey.STOP_DICTATION"

        /**
         * Three states, because the recorder thread can end a recording at the
         * same moment a key press does. Without this, both paths started a
         * transcription and the second one tore the service down while the
         * first was still running.
         */
        private enum class Stage { IDLE, RECORDING, TRANSCRIBING }

        @Volatile
        private var stage = Stage.IDLE

        /** One press starts it, the next ends it. */
        fun toggle(context: Context): String? {
            if (!Voice.isReady(context)) return "Install the speech model in SideKey first"
            if (!TypeService.isEnabled(context)) {
                return "Turn on SideKey Dictation in Settings > Accessibility"
            }

            val intent = Intent(context, DictateService::class.java)
            return when (stage) {
                Stage.IDLE -> {
                    stage = Stage.RECORDING
                    context.startForegroundService(intent)
                    null
                }
                Stage.RECORDING -> {
                    context.startForegroundService(intent.setAction(ACTION_STOP))
                    null
                }
                Stage.TRANSCRIBING -> "Still working on the last one"
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
