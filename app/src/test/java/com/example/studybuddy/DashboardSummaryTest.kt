package com.example.studybuddy

import com.example.studybuddy.model.Priority
import com.example.studybuddy.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardSummaryTest {

    @Test
    fun testDailyProgressPercentage() {
        fun computePercent(completedToday: Int, pending: Int): Int {
            val total = completedToday + pending
            return if (total > 0) (completedToday * 100) / total else 0
        }

        assertEquals(0, computePercent(0, 0))
        assertEquals(100, computePercent(5, 0))
        assertEquals(50, computePercent(2, 2))
        assertEquals(66, computePercent(2, 1))
        assertEquals(25, computePercent(1, 3))
    }

    @Test
    fun testUpcomingTasksOrderingAndFiltering() {
        val now = System.currentTimeMillis()
        val tasks = listOf(
            Task(id = 1, title = "Task Past", dueDate = now - 10000, isCompleted = true),
            Task(id = 2, title = "Task Soonest", dueDate = now + 1000, isCompleted = false, priority = Priority.HIGH),
            Task(id = 3, title = "Task Later", dueDate = now + 50000, isCompleted = false, priority = Priority.MEDIUM),
            Task(id = 4, title = "Task Middle", dueDate = now + 20000, isCompleted = false, priority = Priority.LOW),
            Task(id = 5, title = "Task Already Done", dueDate = now + 5000, isCompleted = true)
        )

        val upcoming = tasks
            .filter { !it.isCompleted }
            .sortedBy { it.dueDate }
            .take(5)

        assertEquals(3, upcoming.size)
        assertEquals("Task Soonest", upcoming[0].title)
        assertEquals("Task Middle", upcoming[1].title)
        assertEquals("Task Later", upcoming[2].title)
        assertTrue(upcoming.all { !it.isCompleted })
    }

    @Test
    fun testStudyMinutesCalculation() {
        fun calculateMinutes(sessions: Int, workDurationMinutes: Int): Int {
            return sessions * workDurationMinutes
        }

        assertEquals(0, calculateMinutes(0, 25))
        assertEquals(25, calculateMinutes(1, 25))
        assertEquals(100, calculateMinutes(4, 25))
        assertEquals(60, calculateMinutes(2, 30))
    }
}
