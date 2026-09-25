package com.example.studybuddy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studybuddy.adapter.SubTaskAdapter
import com.example.studybuddy.databinding.ActivityTaskDetailBinding
import com.example.studybuddy.model.Priority
import com.example.studybuddy.model.SubTask
import com.example.studybuddy.model.categoryIconRes
import com.example.studybuddy.model.displayName
import com.example.studybuddy.notification.AlarmManagerHelper
import com.example.studybuddy.ui.TaskViewModel
import com.example.studybuddy.util.DateUtils

class TaskDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
    }

    private lateinit var binding: ActivityTaskDetailBinding
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var subTaskAdapter: SubTaskAdapter

    private var taskId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        if (taskId == -1) {
            finish()
            return
        }

        taskViewModel = ViewModelProvider(this)[TaskViewModel::class.java]

        setupSubTasks()
        observeTask()

        binding.checkboxComplete.setOnCheckedChangeListener { _, isChecked ->
            taskViewModel.allTasks.value?.firstOrNull { it.id == taskId }?.let { task ->
                taskViewModel.update(
                    task.copy(
                        isCompleted = isChecked,
                        completedAt = if (isChecked) System.currentTimeMillis() else 0L
                    )
                )
                if (isChecked) {
                    AlarmManagerHelper(this).cancelAlarm(taskId)
                }
            }
        }
        binding.buttonEdit.setOnClickListener {
            val intent = Intent(this, AddTaskActivity::class.java)
            intent.putExtra(AddTaskActivity.EXTRA_TASK_ID, taskId)
            startActivity(intent)
        }
        binding.buttonDelete.setOnClickListener {
            taskViewModel.allTasks.value?.firstOrNull { it.id == taskId }?.let { task ->
                taskViewModel.delete(task)
                AlarmManagerHelper(this).cancelAlarm(taskId)
            }
            Toast.makeText(this, "Tarea eliminada", Toast.LENGTH_SHORT).show()
            finish()
        }
        binding.buttonAddSubtask.setOnClickListener { addSubTask() }
    }

    private fun setupSubTasks() {
        subTaskAdapter = SubTaskAdapter(object : SubTaskAdapter.OnSubTaskClickListener {
            override fun onSubTaskStatusChanged(subTask: SubTask, isCompleted: Boolean) {
                taskViewModel.updateSubTask(subTask.copy(isCompleted = isCompleted))
            }

            override fun onSubTaskDelete(subTask: SubTask) {
                taskViewModel.deleteSubTask(subTask)
            }
        })
        binding.recyclerViewSubtasks.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSubtasks.adapter = subTaskAdapter
    }

    private fun observeTask() {
        taskViewModel.allTasks.observe(this) { tasks ->
            val task = tasks.firstOrNull { it.id == taskId } ?: return@observe
            binding.textTitle.text = task.title
            binding.textDescription.text = task.description.ifBlank { "Sin descripción" }
            binding.textDate.text = DateUtils.formatFull(task.dueDate)
            renderCategory(task)
            binding.textPriority.text = priorityLabel(task.priority)
            binding.textTags.text = task.tags.ifBlank { "Sin etiquetas" }
            binding.checkboxComplete.isChecked = task.isCompleted
            binding.textReminder.text = if (task.reminderEnabled) "Recordatorio activado" else "Sin recordatorio"
        }
        taskViewModel.categories.observe(this) { categories ->
            taskViewModel.allTasks.value?.firstOrNull { it.id == taskId }?.let { task ->
                renderCategory(task)
            }
        }
        taskViewModel.subtasksForTask(taskId).observe(this) { subTasks ->
            subTaskAdapter.submitList(subTasks)
            binding.textViewEmptySubtasks.isVisible = subTasks.isEmpty()
        }
    }

    private fun renderCategory(task: com.example.studybuddy.model.Task) {
        val category = (taskViewModel.categories.value ?: emptyList())
            .firstOrNull { it.id == task.categoryId }
        binding.textCategory.text = category?.displayName(this)
            ?: getString(R.string.category_general)
        binding.imageCategory.setImageResource(
            category?.icon?.categoryIconRes() ?: R.drawable.ic_cat_star
        )
        binding.imageCategory.setColorFilter(
            category?.color ?: com.example.studybuddy.model.CategoryEntity.DEFAULT_COLOR
        )
    }

    private fun addSubTask() {
        val title = binding.editTextSubtask.text.toString().trim()
        if (title.isEmpty()) {
            binding.editTextSubtask.error = "Escribe el título de la subtarea"
            return
        }
        taskViewModel.insertSubTask(SubTask(taskId = taskId, title = title))
        binding.editTextSubtask.text?.clear()
    }

    private fun priorityLabel(priority: Int): String = when (priority) {
        Priority.HIGH -> "Alta"
        Priority.LOW -> "Baja"
        else -> "Media"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}