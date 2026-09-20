package com.jakevibes.sidekey

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile

/**
 * Press to start, press again to stop.
 *
 * Lifted from SquareOS, where it was hold-to-talk. Recording is raw PCM
 * written into a WAV, because whisper.cpp wants 16 kHz mono and nothing else.
 * Transcription runs a native binary shipped in jniLibs as a child process,
 * which avoids writing any JNI.
 *
 * The silence detection is kept as a safety net rather than the main way of
 * stopping: a second key press is what normally ends a recording, but if you
 * walk away having forgotten, the room going quiet ends it too.
 */
object Voice {

    const val MODEL_NAME = "ggml-base.en.bin"
    private const val SAMPLE_RATE = 16_000
    /**
     * How the end of a sentence is found.
     *
     * A fixed loudness threshold was the whole problem. In a quiet room a soft
     * voice never crossed it, so it never noticed you had started and never
     * stopped; in a noisy one the room itself sat above it, so it never
     * noticed you had finished. The floor is measured instead, from the first
     * moment of each recording and continuously from the quiet parts, and
     * speech is whatever stands well clear of it.
     *
     * The pause is longer too. 1.2s is inside the range of an ordinary pause
     * for breath, which is why it cut people off mid-sentence.
     */
    private const val SILENCE_SECONDS = 1.9

    /** Speech has to be this much above the room to count. */
    private const val OVER_FLOOR = 2.8

    /** And at least this loud, so a silent room cannot make anything speech. */
    private const val FLOOR_MINIMUM = 260

    /** Long enough to have said something, before any pause can end it. */
    private const val MIN_SPEECH_SECONDS = 0.35

    /** A hard stop, so a stuck microphone cannot record forever. */
    const val MAX_SECONDS = 45

    /**
     * The cap when you are the one deciding when to stop.
     *
     * Longer than the automatic cap, because you may be dictating a paragraph,
     * but not unbounded: whisper runs at roughly half of real time on this
     * phone, so two minutes of talking is over a minute of waiting afterwards.
     */
    const val MAX_MANUAL_SECONDS = 120

    @Volatile
    private var recorder: AudioRecord? = null

    @Volatile
    private var recording = false

    val isRecording: Boolean get() = recording

    /**
     * The model lives in the app's own external files dir so it can be pushed
     * there over adb as well as downloaded. Note that `adb shell mkdir` inside
     * that directory creates a shell-owned folder the app cannot traverse, so
     * the file goes straight in, no subdirectory.
     */
    fun modelDir(context: Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    fun modelFile(context: Context): File = File(modelDir(context), MODEL_NAME)

    fun binary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libwhispercli.so")

    fun isReady(context: Context): Boolean = modelFile(context).exists() && binary(context).exists()

    /** Returns null on success, or why it could not start. */
    @SuppressLint("MissingPermission")
    /**
     * @param stopOnSilence end the recording once the room goes quiet, rather
     *   than waiting to be told. Either way [onEnded] reports that it has.
     * @param maxSeconds a hard cap that always applies, whichever way it ends.
     */
    fun start(
        context: Context,
        onLevel: ((Int) -> Unit)? = null,
        stopOnSilence: Boolean = true,
        maxSeconds: Int = MAX_SECONDS,
        onEnded: (() -> Unit)? = null
    ): String? {
        if (recording) return null
        if (!isReady(context)) return "the speech model is not installed"

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return "this device cannot record at 16 kHz"

        return try {
            val device = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 4
            )
            if (device.state != AudioRecord.STATE_INITIALIZED) return "microphone unavailable"

            recorder = device
            recording = true
            device.startRecording()

            val target = wavFile(context)
            Thread {
                target.outputStream().use { out ->
                    out.write(ByteArray(44))          // header written properly at the end
                    val buffer = ByteArray(minBuffer)
                    var total = 0L
                    var speechBytes = 0L
                    var quietBytes = 0L
                    var floor = -1.0
                    val bytesPerSecond = SAMPLE_RATE * 2

                    while (recording) {
                        val read = device.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            out.write(buffer, 0, read)
                            total += read

                            val level = if (onLevel != null || stopOnSilence) {
                                loudness(buffer, read)
                            } else 0
                            onLevel?.invoke(level)

                            // The cap is outside the silence test on purpose:
                            // when you are the one stopping it, a forgotten
                            // recording still has to end by itself.
                            if (total > bytesPerSecond * maxSeconds) recording = false

                            if (stopOnSilence) {
                                if (floor < 0) floor = level.toDouble()

                                val speaking = level > floor * OVER_FLOOR &&
                                    level > FLOOR_MINIMUM

                                if (speaking) {
                                    speechBytes += read
                                    quietBytes = 0
                                } else {
                                    // The room is re-measured from every quiet
                                    // frame, so a fan starting up or a move to
                                    // a louder place does not break it.
                                    floor = floor * 0.92 + level * 0.08
                                    if (speechBytes > bytesPerSecond * MIN_SPEECH_SECONDS) {
                                        quietBytes += read
                                        if (quietBytes > bytesPerSecond * SILENCE_SECONDS) {
                                            recording = false
                                        }
                                    }
                                }

                            }
                        }
                    }
                    out.flush()
                    writeWavHeader(target, total)
                }
                onEnded?.invoke()
            }.start()
            null
        } catch (e: Exception) {
            recording = false
            "could not start recording: ${e.message}"
        }
    }

    fun stop() {
        recording = false
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
    }

    /** Blocking. Returns the transcript, or null. */
    fun transcribe(context: Context, threads: Int = 4): String? {
        val wav = wavFile(context)
        if (!wav.exists() || wav.length() < 8_000) return null   // shorter than half a second

        return try {
            val process = ProcessBuilder(
                binary(context).absolutePath,
                "-m", modelFile(context).absolutePath,
                "-f", wav.absolutePath,
                "-t", threads.toString(),
                "-nt",        // no timestamps, just the words
                "-np"         // no progress chatter on stdout
            ).redirectErrorStream(false).start()

            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            text.trim().lines().joinToString(" ") { it.trim() }.trim().ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    /** Mean absolute amplitude of a 16-bit buffer, 0 to 32767. */
    private fun loudness(buffer: ByteArray, length: Int): Int {
        var sum = 0L
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xff)).toShort()
            sum += kotlin.math.abs(sample.toInt())
            count++
            i += 2
        }
        return if (count == 0) 0 else (sum / count).toInt()
    }

    private fun wavFile(context: Context) = File(context.cacheDir, "dictation.wav")

    /** WAV needs its length up front, which we only know once recording stops. */
    private fun writeWavHeader(file: File, dataBytes: Long) {
        RandomAccessFile(file, "rw").use { out ->
            out.seek(0)
            out.writeBytes("RIFF")
            out.writeIntLe((36 + dataBytes).toInt())
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLe(16)
            out.writeShortLe(1)                       // PCM
            out.writeShortLe(1)                       // mono
            out.writeIntLe(SAMPLE_RATE)
            out.writeIntLe(SAMPLE_RATE * 2)           // byte rate
            out.writeShortLe(2)                       // block align
            out.writeShortLe(16)                      // bits per sample
            out.writeBytes("data")
            out.writeIntLe(dataBytes.toInt())
        }
    }

    private fun RandomAccessFile.writeIntLe(value: Int) {
        write(byteArrayOf(
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte(),
            ((value shr 16) and 0xff).toByte(),
            ((value shr 24) and 0xff).toByte()
        ))
    }

    private fun RandomAccessFile.writeShortLe(value: Int) {
        write(byteArrayOf((value and 0xff).toByte(), ((value shr 8) and 0xff).toByte()))
    }
}
