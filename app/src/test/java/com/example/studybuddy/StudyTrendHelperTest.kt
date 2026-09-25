package com.example.studybuddy

import com.example.studybuddy.util.StudyTrendHelper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class StudyTrendHelperTest {

    @Test
    fun testStudyHoursMath() {
        fun computeHours(sessions: Int, workMinutes: Int): Double {
            val minutes = sessions * workMinutes
            return String.format(Locale.US, "%.1f", minutes / 60.0).toDouble()
        }

        assertEquals(0.0, computeHours(0, 25), 0.01)
        assertEquals(0.8, computeHours(2, 25), 0.01) // 50 min = 0.8h
        assertEquals(2.5, computeHours(6, 25), 0.01) // 150 min = 2.5h
        assertEquals(4.2, computeHours(10, 25), 0.01) // 250 min = 4.2h
    }

    @Test
    fun testTrendPointDataModel() {
        val point = StudyTrendHelper.TrendPoint(
            label = "Vie 25",
            fullDate = "25 de septiembre",
            hours = 2.5,
            minutes = 150,
            sessions = 6
        )

        assertEquals("Vie 25", point.label)
        assertEquals("25 de septiembre", point.fullDate)
        assertEquals(2.5, point.hours, 0.001)
        assertEquals(150, point.minutes)
        assertEquals(6, point.sessions)
    }

    @Test
    fun testWeeklyAggregationMath() {
        val dailyHours = listOf(1.5, 2.0, 3.2, 2.5, 4.0, 3.0, 2.5)
        val sum = dailyHours.sum()
        val avg = sum / dailyHours.size

        assertEquals(18.7, sum, 0.01)
        assertEquals(2.67, avg, 0.01)
    }
}
