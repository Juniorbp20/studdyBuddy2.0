package com.example.studybuddy

import com.example.studybuddy.model.CategoryEntity
import com.example.studybuddy.model.Task
import com.example.studybuddy.ui.TaskFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryLogicTest {

    @Test
    fun testDefaultCategoriesHaveUniqueIdsAndColors() {
        val defaults = CategoryEntity.defaultCategories()
        assertEquals(4, defaults.size)

        val ids = defaults.map { it.id }.toSet()
        assertEquals(4, ids.size)

        val colors = defaults.map { it.color }.toSet()
        assertEquals(4, colors.size)

        // General, Study, Work, Personal
        val general = defaults.first { it.id == CategoryEntity.ID_GENERAL }
        val study = defaults.first { it.id == CategoryEntity.ID_STUDY }
        val work = defaults.first { it.id == CategoryEntity.ID_WORK }
        val personal = defaults.first { it.id == CategoryEntity.ID_PERSONAL }

        assertEquals("general", general.name)
        assertEquals("study", study.name)
        assertEquals("work", work.name)
        assertEquals("personal", personal.name)

        assertNotEquals(general.color, study.color)
        assertNotEquals(study.color, work.color)
        assertNotEquals(work.color, personal.color)
    }

    @Test
    fun testCustomCategoryPalette() {
        assertTrue(CategoryEntity.CUSTOM_COLORS.isNotEmpty())
        CategoryEntity.CUSTOM_COLORS.forEach { color ->
            assertNotNull(color)
            assertNotEquals(0, color)
        }
    }

    @Test
    fun testTaskFilterByCategory() {
        val filterNone = TaskFilter(category = null)
        assertFalse(filterNone.isActive)

        val filterStudy = TaskFilter(category = CategoryEntity.ID_STUDY)
        assertTrue(filterStudy.isActive)
        assertEquals(CategoryEntity.ID_STUDY, filterStudy.category)

        val tasks = listOf(
            Task(id = 1, title = "Math Homework", categoryId = CategoryEntity.ID_STUDY),
            Task(id = 2, title = "Quarterly Report", categoryId = CategoryEntity.ID_WORK),
            Task(id = 3, title = "Grocery Shopping", categoryId = CategoryEntity.ID_PERSONAL),
            Task(id = 4, title = "Physics Lab", categoryId = CategoryEntity.ID_STUDY)
        )

        val studyTasks = tasks.filter { it.categoryId == filterStudy.category }
        assertEquals(2, studyTasks.size)
        assertTrue(studyTasks.all { it.categoryId == CategoryEntity.ID_STUDY })

        val workTasks = tasks.filter { it.categoryId == CategoryEntity.ID_WORK }
        assertEquals(1, workTasks.size)
        assertEquals("Quarterly Report", workTasks.first().title)
    }
}
