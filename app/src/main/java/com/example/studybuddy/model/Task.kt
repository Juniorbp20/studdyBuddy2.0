package com.example.studybuddy.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "due_date")
    val dueDate: Long = 0L,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "category_id")
    val categoryId: Int = CategoryEntity.ID_GENERAL,

    @ColumnInfo(name = "priority")
    val priority: Int = Priority.MEDIUM,

    @ColumnInfo(name = "reminder_enabled")
    val reminderEnabled: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "completed_at")
    val completedAt: Long = 0L,

    @ColumnInfo(name = "repeat_interval")
    val repeatInterval: Int = RepeatInterval.NONE,

    @ColumnInfo(name = "tags")
    val tags: String = ""
)

object RepeatInterval {
    const val NONE = 0
    const val DAILY = 1
    const val WEEKLY = 7

    val ALL = listOf(NONE, DAILY, WEEKLY)
}

object Priority {
    const val LOW = 0
    const val MEDIUM = 1
    const val HIGH = 2

    val ALL = listOf(LOW, MEDIUM, HIGH)
}