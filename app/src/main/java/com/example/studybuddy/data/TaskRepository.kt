package com.example.studybuddy.data

import android.content.Context
import com.example.studybuddy.model.AppDatabase
import com.example.studybuddy.model.CategoryDao
import com.example.studybuddy.model.CategoryEntity
import com.example.studybuddy.model.DayCount
import com.example.studybuddy.model.SubTask
import com.example.studybuddy.model.SubTaskDao
import com.example.studybuddy.model.Task
import com.example.studybuddy.model.TaskDao
import com.example.studybuddy.ui.TaskFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TaskRepository(private val context: Context) {

    private val database: AppDatabase = AppDatabase.getInstance(context)
    private val taskDao: TaskDao = database.taskDao()
    private val subTaskDao: SubTaskDao = database.subTaskDao()
    private val categoryDao: CategoryDao = database.categoryDao()

    val categories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories().map { list ->
        if (list.isEmpty()) {
            val defaults = CategoryEntity.defaultCategories()
            defaults.forEach { categoryDao.insert(it) }
            defaults
        } else {
            list
        }
    }

    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val totalCount: Flow<Int> = taskDao.getTotalCount()
    val completedCount: Flow<Int> = taskDao.getCompletedCount()
    val pendingCount: Flow<Int> = taskDao.getPendingCount()

    fun upcomingTasks(limit: Int = 5): Flow<List<Task>> = taskDao.getUpcomingTasks(limit)

    fun overdueCount(now: Long): Flow<Int> = taskDao.getOverdueCount(now)

    fun completedToday(startOfDay: Long, endOfDay: Long): Flow<Int> =
        taskDao.getCompletedToday(startOfDay, endOfDay)

    fun completedThisWeek(weekAgo: Long): Flow<Int> = taskDao.getCompletedThisWeek(weekAgo)

    fun completedPerDay(weekStart: Long): Flow<List<DayCount>> =
        taskDao.getCompletedPerDay(weekStart)

    fun tasksInRange(rangeStart: Long, rangeEnd: Long): Flow<List<Task>> =
        taskDao.getTasksInRange(rangeStart, rangeEnd)

    fun pendingCountInRange(rangeStart: Long, rangeEnd: Long): Flow<Int> =
        taskDao.getPendingCountInRange(rangeStart, rangeEnd)

    fun subtasksForTask(taskId: Int): Flow<List<SubTask>> = subTaskDao.getSubtasksForTask(taskId)

    fun getTasks(filter: TaskFilter): Flow<List<Task>> {
        return when {
            !filter.query.isNullOrBlank() -> taskDao.searchTasks(filter.query.trim())
            !filter.tag.isNullOrBlank() -> taskDao.getTasksByTag(filter.tag)
            filter.category != null && filter.priority != null ->
                taskDao.getTasksByCategoryAndPriority(filter.category, filter.priority)
            filter.category != null -> taskDao.getTasksByCategory(filter.category)
            filter.priority != null -> taskDao.getTasksByPriority(filter.priority)
            else -> taskDao.getAllTasks()
        }
    }

    suspend fun upsertCategory(category: CategoryEntity): Int = withContext(Dispatchers.IO) {
        val existing = categoryDao.getByName(category.name)
        if (existing != null) {
            existing.id
        } else {
            categoryDao.insert(category).toInt()
        }
    }

    suspend fun deleteCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        categoryDao.reassignTasksToGeneral(category.id)
        categoryDao.delete(category)
    }

    suspend fun insert(task: Task): Long = withContext(Dispatchers.IO) {
        val id = taskDao.insert(task)
        com.example.studybuddy.util.SyncManager.getInstance(context).enqueueTaskAction(context, id, "INSERT")
        id
    }

    suspend fun update(task: Task) = withContext(Dispatchers.IO) {
        taskDao.update(task)
        com.example.studybuddy.util.SyncManager.getInstance(context).enqueueTaskAction(context, task.id.toLong(), "UPDATE")
    }

    suspend fun delete(task: Task) = withContext(Dispatchers.IO) {
        taskDao.delete(task)
        com.example.studybuddy.util.SyncManager.getInstance(context).enqueueTaskAction(context, task.id.toLong(), "DELETE")
    }

    suspend fun insertSubTask(subTask: SubTask): Long = withContext(Dispatchers.IO) {
        val id = subTaskDao.insert(subTask)
        com.example.studybuddy.util.SyncManager.getInstance(context).enqueueTaskAction(context, subTask.taskId.toLong(), "SUBTASK_INSERT")
        id
    }

    suspend fun updateSubTask(subTask: SubTask) = withContext(Dispatchers.IO) {
        subTaskDao.update(subTask)
        com.example.studybuddy.util.SyncManager.getInstance(context).enqueueTaskAction(context, subTask.taskId.toLong(), "SUBTASK_UPDATE")
    }

    suspend fun deleteSubTask(subTask: SubTask) = withContext(Dispatchers.IO) {
        subTaskDao.delete(subTask)
        com.example.studybuddy.util.SyncManager.getInstance(context).enqueueTaskAction(context, subTask.taskId.toLong(), "SUBTASK_DELETE")
    }
}