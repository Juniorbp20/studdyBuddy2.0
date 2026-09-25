package com.example.studybuddy

import com.example.studybuddy.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateUtilsTest {

    @Test
    fun startOfDay_returnsMidnight() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = DateUtils.startOfDay(1786800000000L)
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun startOfDay_roundsToMidnight() {
        val dayStart = DateUtils.startOfDay(1786800000000L)
        assertTrue(dayStart <= 1786800000000L)
        assertEquals(dayStart, DateUtils.startOfDay(dayStart + 12 * 3600 * 1000L))
    }

    @Test
    fun isOverdue_returnsFalseForZero() {
        assertFalse(DateUtils.isOverdue(0L))
    }

    @Test
    fun isOverdue_returnsFalseForFuture() {
        assertFalse(DateUtils.isOverdue(System.currentTimeMillis() + 100_000L))
    }

    @Test
    fun isOverdue_returnsTrueForPast() {
        assertTrue(DateUtils.isOverdue(System.currentTimeMillis() - 100_000L))
    }

    @Test
    fun formatTime_returnsPlaceholderForZero() {
        assertEquals("--:--", DateUtils.formatTime(0L))
    }

    @Test
    fun formatDate_returnsPlaceholderForZero() {
        assertEquals("Sin fecha", DateUtils.formatDate(0L))
    }
}