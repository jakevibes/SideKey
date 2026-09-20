package com.jakevibes.sidekey

import android.app.DownloadManager
import android.content.Context
import android.net.Uri

/**
 * The 142 MB that cannot ship in the APK.
 *
 * Downloaded through DownloadManager rather than by hand, so it survives the
 * app being closed, resumes, and shows progress in the shade like any other
 * download. It lands in the app's own external files dir, which is also where
 * you would push it over adb if you would rather not wait.
 */
object SpeechModel {

    /** whisper.cpp's own published weights. */
    const val URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/${Voice.MODEL_NAME}"

    const val MEGABYTES = 142

    fun installed(context: Context): Boolean = Voice.modelFile(context).exists()

    /** Whether a download is already running, so it is not started twice. */
    fun downloading(context: Context): Boolean {
        val id = Prefs(context).modelDownload
        if (id == 0L) return false
        val manager = context.getSystemService(DownloadManager::class.java) ?: return false
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return false
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return status == DownloadManager.STATUS_RUNNING ||
                status == DownloadManager.STATUS_PENDING ||
                status == DownloadManager.STATUS_PAUSED
        }
    }

    fun download(context: Context): String? {
        if (installed(context)) return "Already installed"
        if (downloading(context)) return "Already downloading"
        val manager = context.getSystemService(DownloadManager::class.java)
            ?: return "No download manager on this phone"

        return try {
            val request = DownloadManager.Request(Uri.parse(URL))
                .setTitle("SideKey speech model")
                .setDescription("$MEGABYTES MB, once")
                .setAllowedOverMetered(false)
                .setDestinationInExternalFilesDir(context, null, Voice.MODEL_NAME)
            Prefs(context).modelDownload = manager.enqueue(request)
            "Downloading over Wi-Fi"
        } catch (e: Exception) {
            "Could not start: ${e.message}"
        }
    }
}
