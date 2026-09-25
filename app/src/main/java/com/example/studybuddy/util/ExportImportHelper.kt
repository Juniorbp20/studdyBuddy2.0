package com.example.studybuddy.util

import android.content.Context
import android.net.Uri
import com.example.studybuddy.model.CategoryEntity
import com.example.studybuddy.model.Task
import org.json.JSONArray
import org.json.JSONObject

object ExportImportHelper {

    private const val MIME_TYPE = "application/json"

    data class ImportResult(
        val tasks: List<Task>,
        val categories: List<CategoryEntity>
    )

    fun exportToJson(tasks: List<Task>, categories: List<CategoryEntity>): String {
        val taskArray = JSONArray()
        tasks.forEach { task ->
            val obj = JSONObject()
                .put("id", task.id)
                .put("title", task.title)
                .put("description", task.description)
                .put("dueDate", task.dueDate)
                .put("isCompleted", task.isCompleted)
                .put("categoryId", task.categoryId)
                .put("priority", task.priority)
                .put("reminderEnabled", task.reminderEnabled)
                .put("createdAt", task.createdAt)
                .put("repeatInterval", task.repeatInterval)
                .put("tags", task.tags)
            taskArray.put(obj)
        }
        val categoryArray = JSONArray()
        categories.forEach { category ->
            val obj = JSONObject()
                .put("id", category.id)
                .put("name", category.name)
                .put("icon", category.icon)
                .put("color", category.color)
            categoryArray.put(obj)
        }
        return JSONObject()
            .put("tasks", taskArray)
            .put("categories", categoryArray)
            .toString(2)
    }

    fun importFromJson(json: String): ImportResult {
        val root = JSONObject(json)
        val categories = mutableListOf<CategoryEntity>()
        if (root.has("categories")) {
            val categoryArray = root.getJSONArray("categories")
            for (i in 0 until categoryArray.length()) {
                val obj = categoryArray.getJSONObject(i)
                categories.add(
                    CategoryEntity(
                        id = obj.optInt("id", 0),
                        name = obj.optString("name", ""),
                        icon = obj.optString("icon", CategoryEntity.DEFAULT_ICON),
                        color = obj.optInt("color", CategoryEntity.DEFAULT_COLOR)
                    )
                )
            }
        }
        val array = root.getJSONArray("tasks")
        val tasks = mutableListOf<Task>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            tasks.add(
                Task(
                    id = 0,
                    title = obj.optString("title", ""),
                    description = obj.optString("description", ""),
                    dueDate = obj.optLong("dueDate", 0L),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    categoryId = obj.optInt("categoryId", CategoryEntity.ID_GENERAL),
                    priority = obj.optInt("priority", 1),
                    reminderEnabled = obj.optBoolean("reminderEnabled", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    completedAt = obj.optLong("completedAt", 0L),
                    repeatInterval = obj.optInt("repeatInterval", 0),
                    tags = obj.optString("tags", "")
                )
            )
        }
        return ImportResult(tasks.filter { it.title.isNotBlank() }, categories)
    }

    fun exportToCsv(tasks: List<Task>): String {
        val header =
            "title,description,dueDate,isCompleted,categoryId,priority,reminderEnabled,tags"
        val rows = tasks.joinToString("\n") { task ->
            listOf(
                escapeCsv(task.title),
                escapeCsv(task.description),
                task.dueDate.toString(),
                task.isCompleted.toString(),
                task.categoryId.toString(),
                task.priority.toString(),
                task.reminderEnabled.toString(),
                escapeCsv(task.tags)
            ).joinToString(",")
        }
        return "$header\n$rows"
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n')) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray())
            } != null
        } catch (e: Exception) {
            false
        }
    }

    fun readFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun defaultExportFileName(): String {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault())
            .format(java.util.Date())
        return "studybuddy_tasks_$timestamp.json"
    }
}