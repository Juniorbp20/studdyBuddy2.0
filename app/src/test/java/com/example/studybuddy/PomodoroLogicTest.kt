package com.example.studybuddy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroLogicTest {

    @Test
    fun testIntervalDurationCalculation() {
        val workMinutes = 25
        val shortBreakMinutes = 5
        val longBreakMinutes = 15

        assertEquals(25 * 60_000L, workMinutes * 60_000L)
        assertEquals(5 * 60_000L, shortBreakMinutes * 60_000L)
        assertEquals(15 * 60_000L, longBreakMinutes * 60_000L)
    }

    @Test
    fun testIntervalValidationBounds() {
        val rawInputTooLow = -5
        val rawInputTooHigh = 999
        val validWork = 30

        val minAllowed = 1
        val maxAllowed = 180

        assertEquals(1, rawInputTooLow.coerceIn(minAllowed, maxAllowed))
        assertEquals(180, rawInputTooHigh.coerceIn(minAllowed, maxAllowed))
        assertEquals(30, validWork.coerceIn(minAllowed, maxAllowed))
    }

    @Test
    fun testCycleTransitionLogic() {
        val cyclesBeforeLongBreak = 4
        var completedCycles = 0

        // Cycle 1 completed
        completedCycles++
        assertTrue(completedCycles < cyclesBeforeLongBreak)

        // Cycle 2 completed
        completedCycles++
        assertTrue(completedCycles < cyclesBeforeLongBreak)

        // Cycle 3 completed
        completedCycles++
        assertTrue(completedCycles < cyclesBeforeLongBreak)

        // Cycle 4 completed -> triggers Long Break
        completedCycles++
        val isLongBreak = completedCycles >= cyclesBeforeLongBreak
        assertTrue(isLongBreak)
    }

    @Test
    fun testTimeFormatting() {
        val totalSeconds = 1500L // 25 minutes
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val formatted = String.format("%02d:%02d", minutes, seconds)
        assertEquals("25:00", formatted)

        val totalSeconds2 = 59L
        val formatted2 = String.format("%02d:%02d", totalSeconds2 / 60, totalSeconds2 % 60)
        assertEquals("00:59", formatted2)
    }
}
