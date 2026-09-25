package com.example.studybuddy

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddy.adapter.TaskAdapter
import com.example.studybuddy.databinding.ActivityCalendarBinding
import com.example.studybuddy.ui.TaskViewModel
import com.example.studybuddy.util.DateUtils
import com.example.studybuddy.view.MonthCalendarView
import java.util.Calendar
import java.util.Locale

class CalendarActivity : AppCompatActivity(), TaskAdapter.OnItemClickListener {

    private lateinit var binding: ActivityCalendarBinding
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var adapter: TaskAdapter

    private var currentMonth = Calendar.getInstance()
    private var selectedDay: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        taskViewModel = ViewModelProvider(this)[TaskViewModel::class.java]
        adapter = TaskAdapter(this)
        binding.recyclerViewDayTasks.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewDayTasks.adapter = adapter
        taskViewModel.categories.observe(this) { categories ->
            adapter.updateCategories(categories)
        }

        updateMonthTitle()
        binding.monthCalendar.setMonth(currentMonth)
        binding.monthCalendar.setDayBackgrounds(emptySet())
        binding.monthCalendar.onDaySelected = { day ->
            selectedDay = day
            loadDayTasks()
        }

        binding.buttonPrevMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, -1)
            loadMonth()
        }
        binding.buttonNextMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, 1)
            loadMonth()
        }
        loadMonth()
    }

    private fun loadMonth() {
        updateMonthTitle()
        binding.monthCalendar.setMonth(currentMonth)
        binding.monthCalendar.selectedDay = null
        binding.monthCalendar.invalidate()

        val start = DateUtils.startOfDay(currentMonth.timeInMillis)
        val end = start + 42L * 24 * 60 * 60 * 1000
        taskViewModel.pendingCountInRange(start, end).observe(this) { count ->
            if (count == 0) {
                binding.monthCalendar.setDayBackgrounds(emptySet())
                return@observe
            }
        }
        taskViewModel.tasksInRange(start, end).observe(this) { tasks ->
            val days = tasks.map {
                DateUtils.startOfDay(it.dueDate) / 86400000L
            }.toSet()
            binding.monthCalendar.setDayBackgrounds(days)
        }
        loadDayTasks()
    }

    private fun loadDayTasks() {
        val start = DateUtils.startOfDay(selectedDay.timeInMillis)
        val end = start + 24 * 60 * 60 * 1000
        taskViewModel.tasksInRange(start, end).observe(this) { tasks ->
            adapter.submitList(tasks)
            binding.textViewEmpty.isVisible = tasks.isEmpty()
            binding.recyclerViewDayTasks.isVisible = tasks.isNotEmpty()
        }
        binding.textViewSelectedDay.text = DateUtils.formatDate(start)
    }

    private fun updateMonthTitle() {
        val fmt = java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        binding.textViewMonthTitle.text = fmt.format(currentMonth.time).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    override fun onItemClick(task: com.example.studybuddy.model.Task) {
        val intent = android.content.Intent(this, TaskDetailActivity::class.java)
        intent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id)
        startActivity(intent)
    }

    override fun onTaskStatusChanged(
        task: com.example.studybuddy.model.Task,
        isCompleted: Boolean
    ) {
        taskViewModel.update(
            task.copy(
                isCompleted = isCompleted,
                completedAt = if (isCompleted) System.currentTimeMillis() else 0L
            )
        )
        if (isCompleted) {
            com.example.studybuddy.notification.AlarmManagerHelper(this).cancelAlarm(task.id)
        }
    }

    override fun onTaskDelete(task: com.example.studybuddy.model.Task) {
        taskViewModel.delete(task)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}