package com.example.studybuddy.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddy.R
import com.example.studybuddy.databinding.ItemTaskBinding
import com.example.studybuddy.model.CategoryEntity
import com.example.studybuddy.model.Priority
import com.example.studybuddy.model.Task
import com.example.studybuddy.model.categoryIconRes
import com.example.studybuddy.util.DateUtils

class TaskAdapter(
    private val listener: OnItemClickListener
) : ListAdapter<Task, TaskAdapter.TaskHolder>(DIFF_CALLBACK) {

    private val categories = mutableMapOf<Int, CategoryEntity>()

    fun updateCategories(list: List<CategoryEntity>) {
        categories.clear()
        list.forEach { categories[it.id] = it }
        notifyDataSetChanged()
    }

    interface OnItemClickListener {
        fun onItemClick(task: Task)
        fun onTaskStatusChanged(task: Task, isCompleted: Boolean)
        fun onTaskDelete(task: Task)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskHolder {
        val binding = ItemTaskBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TaskHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskHolder(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.textViewTaskTitle.text = task.title
            binding.textViewTaskDate.text = DateUtils.formatDate(task.dueDate)
            binding.textViewTaskTime.text = DateUtils.formatTime(task.dueDate)
            binding.checkboxTaskCompleted.isChecked = task.isCompleted

            val category = categories[task.categoryId]
            val iconRes = category?.icon?.categoryIconRes()
                ?: R.drawable.ic_cat_star
            binding.imageCategory.setImageResource(iconRes)
            binding.imageCategory.setColorFilter(categoryColor(task.categoryId))
            binding.imagePriority.setColorFilter(priorityColor(task.priority))

            val completed = task.isCompleted
            val overdue = !completed && DateUtils.isOverdue(task.dueDate)

            binding.textViewTaskTitle.alpha = if (completed) 0.5f else 1f
            binding.textViewTaskDate.alpha = if (completed) 0.5f else 1f
            binding.textViewTaskTime.alpha = if (completed) 0.5f else 1f
            binding.textViewTaskDate.setTextColor(
                if (overdue) Color.RED else binding.textViewTaskDate.currentTextColor
            )

            binding.root.setOnClickListener { listener.onItemClick(task) }
            binding.checkboxTaskCompleted.setOnCheckedChangeListener(null)
            binding.checkboxTaskCompleted.setOnCheckedChangeListener { _, isChecked ->
                listener.onTaskStatusChanged(task, isChecked)
            }
            binding.buttonDeleteTask.setOnClickListener { listener.onTaskDelete(task) }
        }
    }

    private fun categoryColor(categoryId: Int): Int {
        val category = categories[categoryId]
        return category?.color ?: CategoryEntity.DEFAULT_COLOR
    }

    private fun priorityColor(priority: Int): Int = when (priority) {
        Priority.HIGH -> Color.parseColor("#E53935")
        Priority.MEDIUM -> Color.parseColor("#FB8C00")
        else -> Color.parseColor("#43A047")
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Task>() {
            override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean =
                oldItem == newItem
        }
    }
}