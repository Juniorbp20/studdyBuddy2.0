package com.example.studybuddy.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddy.R
import com.example.studybuddy.databinding.ItemUpcomingTaskBinding
import com.example.studybuddy.model.CategoryEntity
import com.example.studybuddy.model.Priority
import com.example.studybuddy.model.Task
import com.example.studybuddy.model.categoryIconRes
import com.example.studybuddy.model.displayName
import com.example.studybuddy.util.DateUtils

class UpcomingTaskAdapter(
    private val listener: OnUpcomingTaskClickListener
) : ListAdapter<Task, UpcomingTaskAdapter.UpcomingTaskHolder>(TaskDiffCallback()) {

    private val categories = mutableMapOf<Int, CategoryEntity>()

    fun updateCategories(list: List<CategoryEntity>) {
        categories.clear()
        list.forEach { categories[it.id] = it }
        notifyDataSetChanged()
    }

    interface OnUpcomingTaskClickListener {
        fun onItemClick(task: Task)
        fun onTaskCompleted(task: Task, isCompleted: Boolean)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UpcomingTaskHolder {
        val binding = ItemUpcomingTaskBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return UpcomingTaskHolder(binding)
    }

    override fun onBindViewHolder(holder: UpcomingTaskHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UpcomingTaskHolder(private val binding: ItemUpcomingTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            val context = binding.root.context
            binding.textUpcomingTitle.text = task.title

            val formattedDate = DateUtils.formatDate(task.dueDate)
            val formattedTime = DateUtils.formatTime(task.dueDate)
            binding.textUpcomingDate.text = "$formattedDate, $formattedTime"

            val category = categories[task.categoryId]
            val catColor = category?.color ?: CategoryEntity.DEFAULT_COLOR
            val iconRes = category?.icon?.categoryIconRes() ?: R.drawable.ic_cat_star
            val catName = category?.displayName(context) ?: context.getString(R.string.category_general)

            // Category indicator bar & pill
            binding.viewUpcomingCategoryIndicator.backgroundTintList = ColorStateList.valueOf(catColor)
            binding.imageUpcomingCategory.setImageResource(iconRes)
            binding.imageUpcomingCategory.setColorFilter(catColor)
            binding.textUpcomingCategoryName.text = catName
            binding.textUpcomingCategoryName.setTextColor(catColor)

            val badgeBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.resources.displayMetrics.density * 6
                setColor(ColorUtils.setAlphaComponent(catColor, 35))
            }
            binding.layoutUpcomingCategoryBadge.background = badgeBackground

            // Priority indicator
            val priorityColor = when (task.priority) {
                Priority.HIGH -> Color.parseColor("#EF4444")
                Priority.MEDIUM -> Color.parseColor("#F59E0B")
                else -> Color.parseColor("#10B981")
            }
            binding.imageUpcomingPriority.setColorFilter(priorityColor)

            // Overdue check
            val isOverdue = !task.isCompleted && DateUtils.isOverdue(task.dueDate)
            binding.textUpcomingOverdue.visibility = if (isOverdue) View.VISIBLE else View.GONE
            if (isOverdue) {
                binding.textUpcomingDate.setTextColor(Color.parseColor("#DC2626"))
            } else {
                binding.textUpcomingDate.setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        binding.root, android.R.attr.textColorSecondary
                    )
                )
            }

            // Checkbox and item click
            binding.checkboxUpcomingCompleted.setOnCheckedChangeListener(null)
            binding.checkboxUpcomingCompleted.isChecked = task.isCompleted
            binding.checkboxUpcomingCompleted.setOnCheckedChangeListener { _, isChecked ->
                listener.onTaskCompleted(task, isChecked)
            }

            binding.root.setOnClickListener {
                listener.onItemClick(task)
            }
        }
    }

    private class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean =
            oldItem == newItem
    }
}
