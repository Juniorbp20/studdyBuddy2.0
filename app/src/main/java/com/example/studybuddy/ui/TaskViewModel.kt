package com.example.studybuddy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.studybuddy.data.ServiceLocator
import com.example.studybuddy.data.TaskRepository
import com.example.studybuddy.model.CategoryEntity
import com.example.studybuddy.model.DayCount
import com.example.studybuddy.model.SubTask
import com.example.studybuddy.model.Task
import com.example.studybuddy.util.DateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class TaskFilter(
    val query: String? = null,
    val category: Int? = null,
    val priority: Int? = null,
    val tag: String? = null
) {
    val isActive: Boolean
        get() = !query.isNullOrBlank() || category != null ||
            priority != null || !tag.isNullOrBlank()
}

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TaskRepository = ServiceLocator.getRepository(application)

    private val _searchQuery = MutableStateFlow<String?>(null)
    private val _categoryFilter = MutableStateFlow<Int?>(null)
    private val _priorityFilter = MutableStateFlow<Int?>(null)
    private val _tagFilter = MutableStateFlow<String?>(null)

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val allTasks: LiveData<List<Task>> = repository.allTasks.asLiveData()
    val totalCount: LiveData<Int> = repository.totalCount.asLiveData()
    val completedCount: LiveData<Int> = repository.completedCount.asLiveData()
    val pendingCount: LiveData<Int> = repository.pendingCount.asLiveData()
    val categories: LiveData<List<CategoryEntity>> = repository.categories.asLiveData()

    val overdueCount: LiveData<Int> = ticker.flatMapLatest { now ->
        repository.overdueCount(now)
    }.asLiveData()

    val completedToday: LiveData<Int> = ticker.flatMapLatest { now ->
        repository.completedToday(
            DateUtils.startOfDay(now),
            DateUtils.startOfDay(now) + 24 * 60 * 60 * 1000
        )
    }.asLiveData()

    val completedThisWeek: LiveData<Int> = ticker.flatMapLatest { now ->
        repository.completedThisWeek(now - 7L * 24 * 60 * 60 * 1000)
    }.asLiveData()

    val completedPerDay: LiveData<List<DayCount>> = ticker.flatMapLatest { now ->
        repository.completedPerDay(now - 7L * 24 * 60 * 60 * 1000)
    }.asLiveData()

    val filteredTasks: LiveData<List<Task>> =
        combine(
            _searchQuery,
            _categoryFilter,
            _priorityFilter,
            _tagFilter
        ) { query, category, priority, tag ->
            TaskFilter(query, category, priority, tag)
        }
            .flatMapLatest { repository.getTasks(it) }
            .asLiveData()

    val availableTags: LiveData<List<String>> = repository.allTasks.map { tasks ->
        tasks.flatMap { it.tags.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }.asLiveData()

    fun tasksInRange(start: Long, end: Long): LiveData<List<Task>> =
        repository.tasksInRange(start, end).asLiveData()

    fun pendingCountInRange(start: Long, end: Long): LiveData<Int> =
        repository.pendingCountInRange(start, end).asLiveData()

    fun setSearchQuery(query: String?) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: Int?) {
        _categoryFilter.value = category
    }

    fun setPriorityFilter(priority: Int?) {
        _priorityFilter.value = priority
    }

    fun setTagFilter(tag: String?) {
        _tagFilter.value = tag
    }

    fun subtasksForTask(taskId: Int): LiveData<List<SubTask>> =
        repository.subtasksForTask(taskId).asLiveData()

    fun insert(task: Task, onInserted: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insert(task)
            onInserted(id)
        }
    }

    fun update(task: Task) {
        viewModelScope.launch {
            repository.update(task)
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            repository.delete(task)
        }
    }

    fun insertSubTask(subTask: SubTask) {
        viewModelScope.launch {
            repository.insertSubTask(subTask)
        }
    }

    fun updateSubTask(subTask: SubTask) {
        viewModelScope.launch {
            repository.updateSubTask(subTask)
        }
    }

    fun deleteSubTask(subTask: SubTask) {
        viewModelScope.launch {
            repository.deleteSubTask(subTask)
        }
    }

    fun upsertCategory(category: CategoryEntity, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.upsertCategory(category)
            onDone(id)
        }
    }

    suspend fun upsertCategoryNow(category: CategoryEntity): Int =
        repository.upsertCategory(category)

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }
}