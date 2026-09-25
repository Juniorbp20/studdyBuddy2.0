package com.example.studybuddy

import com.example.studybuddy.util.StudyGoalManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class StudyGoalManagerTest {

    @Test
    fun testTargetMinutesMath() {
        fun toMinutes(hours: Float): Int = (hours * 60).roundToInt()

        assertEquals(30, toMinutes(0.5f))
        assertEquals(60, toMinutes(1.0f))
        assertEquals(90, toMinutes(1.5f))
        assertEquals(120, toMinutes(2.0f))
        assertEquals(150, toMinutes(2.5f))
        assertEquals(180, toMinutes(3.0f))
        assertEquals(240, toMinutes(4.0f))
    }

    @Test
    fun testRemainingAndProgressCalculations() {
        fun calcRemaining(targetMinutes: Int, completedMinutes: Int): Int =
            maxOf(0, targetMinutes - completedMinutes)

        fun calcPercent(targetMinutes: Int, completedMinutes: Int): Int {
            if (targetMinutes <= 0) return 100
            return minOf(100, (completedMinutes * 100) / targetMinutes)
        }

        fun isMet(targetMinutes: Int, completedMinutes: Int): Boolean =
            completedMinutes >= targetMinutes

        val target = 120 // 2 hours

        // Scenario 1: Not started
        assertEquals(120, calcRemaining(target, 0))
        assertEquals(0, calcPercent(target, 0))
        assertFalse(isMet(target, 0))

        // Scenario 2: Halfway
        assertEquals(60, calcRemaining(target, 60))
        assertEquals(50, calcPercent(target, 60))
        assertFalse(isMet(target, 60))

        // Scenario 3: Almost there
        assertEquals(20, calcRemaining(target, 100))
        assertEquals(83, calcPercent(target, 100))
        assertFalse(isMet(target, 100))

        // Scenario 4: Exactly reached
        assertEquals(0, calcRemaining(target, 120))
        assertEquals(100, calcPercent(target, 120))
        assertTrue(isMet(target, 120))

        // Scenario 5: Exceeded
        assertEquals(0, calcRemaining(target, 150))
        assertEquals(100, calcPercent(target, 150))
        assertTrue(isMet(target, 150))
    }

    @Test
    fun testDurationFormatting() {
        assertEquals("25 min", StudyGoalManager.formatDuration(25))
        assertEquals("59 min", StudyGoalManager.formatDuration(59))
        assertEquals("1h", StudyGoalManager.formatDuration(60))
        assertEquals("1h 15m", StudyGoalManager.formatDuration(75))
        assertEquals("2h", StudyGoalManager.formatDuration(120))
        assertEquals("2h 30m", StudyGoalManager.formatDuration(150))
    }

    @Test
    fun testReminderEligibilityLogic() {
        fun shouldRemind(
            isGoalMet: Boolean,
            remindersEnabled: Boolean,
            lastDate: String?,
            today: String,
            lastMillis: Long,
            now: Long
        ): Boolean {
            if (!remindersEnabled) return false
            if (isGoalMet) return false
            if (lastDate != today) return true
            return (now - lastMillis) >= 2 * 60 * 60 * 1000L
        }

        val today = "20260925"
        val now = 1000000000L

        // Goal already met -> should not remind
        assertFalse(shouldRemind(true, true, null, today, 0L, now))

        // Reminders disabled -> should not remind
        assertFalse(shouldRemind(false, false, null, today, 0L, now))

        // Goal not met & first open today -> should remind!
        assertTrue(shouldRemind(false, true, "20260924", today, now - 5000, now))

        // Goal not met & reminded 10 minutes ago today -> cooldown active
        assertFalse(shouldRemind(false, true, today, today, now - (10 * 60 * 1000L), now))

        // Goal not met & reminded 3 hours ago today -> should remind again!
        assertTrue(shouldRemind(false, true, today, today, now - (3 * 60 * 60 * 1000L), now))
    }
}
