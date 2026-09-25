package com.example.studybuddy.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddy.databinding.ItemSubtaskBinding
import com.example.studybuddy.model.SubTask

class SubTaskAdapter(
    private val listener: OnSubTaskClickListener
) : ListAdapter<SubTask, SubTaskAdapter.SubTaskHolder>(DIFF_CALLBACK) {

    interface OnSubTaskClickListener {
        fun onSubTaskStatusChanged(subTask: SubTask, isCompleted: Boolean)
        fun onSubTaskDelete(subTask: SubTask)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubTaskHolder {
        val binding = ItemSubtaskBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SubTaskHolder(binding)
    }

    override fun onBindViewHolder(holder: SubTaskHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SubTaskHolder(private val binding: ItemSubtaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(subTask: SubTask) {
            binding.checkboxSubtask.isChecked = subTask.isCompleted
            binding.textSubtaskTitle.text = subTask.title
            binding.textSubtaskTitle.alpha = if (subTask.isCompleted) 0.5f else 1f
            binding.checkboxSubtask.setOnCheckedChangeListener(null)
            binding.checkboxSubtask.setOnCheckedChangeListener { _, isChecked ->
                listener.onSubTaskStatusChanged(subTask, isChecked)
            }
            binding.buttonDeleteSubtask.setOnClickListener {
                listener.onSubTaskDelete(subTask)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SubTask>() {
            override fun areItemsTheSame(oldItem: SubTask, newItem: SubTask): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: SubTask, newItem: SubTask): Boolean =
                oldItem == newItem
        }
    }
}