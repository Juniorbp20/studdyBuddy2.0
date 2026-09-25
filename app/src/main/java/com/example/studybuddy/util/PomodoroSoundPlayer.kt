package com.example.studybuddy.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object PomodoroSoundPlayer {

    private const val TAG = "PomodoroSound"
    private const val PREFS_NAME = "focus_prefs"
    const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val SAMPLE_RATE = 44100

    fun isSoundEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SOUND_ENABLED, enabled)
            .apply()
    }

    /**
     * Plays a subtle, non-intrusive sound alert if sound is enabled.
     * @param isFocusEnd true if a focus session just ended (time for break),
     *                   false if a break just ended (time to focus).
     */
    fun playSessionAlert(context: Context, isFocusEnd: Boolean, force: Boolean = false) {
        if (!force && !isSoundEnabled(context)) return

        CoroutineScope(Dispatchers.Default).launch {
            try {
                playHarmonicChime(isFocusEnd)
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack playback failed, falling back to system notification sound: ${e.message}")
                playSystemNotificationFallback(context)
            }
        }
    }

    /**
     * Generates a warm, organic bell-like chime:
     * - Dual gentle tones
     * - Smooth linear attack (prevents abrupt click/pop)
     * - Smooth exponential decay
     * - Rich fundamental + subtle 2nd & 3rd harmonic timbre
     * - Moderate amplitude (0.35) so it is gentle and relaxing
     */
    private fun playHarmonicChime(isFocusEnd: Boolean) {
        val tone1Freq: Double
        val tone2Freq: Double
        val tone1Ms: Int
        val tone2Ms: Int

        if (isFocusEnd) {
            // Focus ended -> Peaceful descending relaxation chime (E5 -> C5)
            tone1Freq = 659.25 // E5
            tone2Freq = 523.25 // C5
            tone1Ms = 320
            tone2Ms = 680
        } else {
            // Break ended -> Gentle ascending renewal chime (C5 -> E5)
            tone1Freq = 523.25 // C5
            tone2Freq = 659.25 // E5
            tone1Ms = 320
            tone2Ms = 680
        }

        val part1Samples = generateChimeTone(tone1Freq, tone1Ms, maxAmplitude = 0.32f, decayRate = 4.2)
        val part2Samples = generateChimeTone(tone2Freq, tone2Ms, maxAmplitude = 0.35f, decayRate = 3.6)

        val totalSamples = ShortArray(part1Samples.size + part2Samples.size)
        System.arraycopy(part1Samples, 0, totalSamples, 0, part1Samples.size)
        System.arraycopy(part2Samples, 0, totalSamples, part1Samples.size, part2Samples.size)

        val bufferSizeInBytes = totalSamples.size * 2

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSizeInBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(totalSamples, 0, totalSamples.size)
        audioTrack.play()

        val durationMillis = (totalSamples.size * 1000L) / SAMPLE_RATE
        try {
            Thread.sleep(durationMillis + 100)
        } catch (_: InterruptedException) {
        } finally {
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {
            }
        }
    }

    internal fun generateChimeTone(
        frequency: Double,
        durationMs: Int,
        maxAmplitude: Float,
        decayRate: Double
    ): ShortArray {
        val numSamples = (durationMs * SAMPLE_RATE) / 1000
        val samples = ShortArray(numSamples)

        val attackSamples = (SAMPLE_RATE * 0.025).toInt() // 25ms soft attack
        val durationSec = durationMs / 1000.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE

            // Smooth envelope: linear fade-in attack + exponential decay
            val attack = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
            val decay = exp(-decayRate * (t / durationSec))
            val envelope = attack * decay

            // Fundamental + 2nd harmonic (octave) at 20% + 3rd harmonic at 8% for organic bell character
            val wave = sin(2.0 * PI * frequency * t) +
                0.20 * sin(2.0 * PI * (frequency * 2.0) * t) +
                0.08 * sin(2.0 * PI * (frequency * 3.0) * t)

            val sampleVal = (wave * envelope * maxAmplitude * Short.MAX_VALUE).toInt()
            samples[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return samples
    }

    private fun playSystemNotificationFallback(context: Context) {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, notificationUri)
            ringtone?.play()
        } catch (_: Exception) {
        }
    }
}
