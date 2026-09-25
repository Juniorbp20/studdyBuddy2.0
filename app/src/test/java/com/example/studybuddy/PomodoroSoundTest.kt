package com.example.studybuddy

import com.example.studybuddy.util.PomodoroSoundPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PomodoroSoundTest {

    @Test
    fun testToneSampleCount() {
        val sampleRate = 44100
        val durationMs = 300
        val expectedSamples = (durationMs * sampleRate) / 1000

        val samples = PomodoroSoundPlayer.generateChimeTone(
            frequency = 523.25,
            durationMs = durationMs,
            maxAmplitude = 0.35f,
            decayRate = 3.5
        )

        assertEquals(expectedSamples, samples.size)
    }

    @Test
    fun testToneAmplitudeBoundsAndNoClipping() {
        val durationMs = 500
        val maxAmplitude = 0.35f

        val samples = PomodoroSoundPlayer.generateChimeTone(
            frequency = 659.25,
            durationMs = durationMs,
            maxAmplitude = maxAmplitude,
            decayRate = 4.0
        )

        assertTrue(samples.isNotEmpty())

        val theoreticalMax = (maxAmplitude * 1.30f * Short.MAX_VALUE).toInt() // with harmonics
        for (sample in samples) {
            assertTrue("Sample should not exceed 16-bit bounds", sample >= Short.MIN_VALUE && sample <= Short.MAX_VALUE)
            assertTrue("Sample amplitude should remain subtle", abs(sample.toInt()) <= theoreticalMax + 100)
        }
    }

    @Test
    fun testEnvelopeDecay() {
        val durationMs = 600
        val samples = PomodoroSoundPlayer.generateChimeTone(
            frequency = 523.25,
            durationMs = durationMs,
            maxAmplitude = 0.35f,
            decayRate = 4.0
        )

        val peakInEarlyHalf = samples.slice(0 until samples.size / 2).maxOf { abs(it.toInt()) }
        val tailInLateHalf = samples.slice((samples.size * 3 / 4) until samples.size).maxOf { abs(it.toInt()) }

        assertTrue("Sound should fade out significantly towards the tail for a gentle finish", tailInLateHalf < peakInEarlyHalf / 2)
    }
}
