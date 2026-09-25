package com.example.studybuddy

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.studybuddy.databinding.ActivityStatisticsBinding
import com.example.studybuddy.model.DayCount
import com.example.studybuddy.ui.TaskViewModel
import com.example.studybuddy.util.DateUtils
import com.example.studybuddy.util.FocusSessionStore
import com.example.studybuddy.view.BarChartView
import java.text.SimpleDateFormat
import java.util.Locale

class StatisticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatisticsBinding
    private lateinit var taskViewModel: TaskViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        taskViewModel = ViewModelProvider(this)[TaskViewModel::class.java]

        taskViewModel.totalCount.observe(this) {
            binding.textTotalTasks.text = it.toString()
        }
        taskViewModel.completedCount.observe(this) {
            binding.textCompletedTasks.text = it.toString()
            updateProgress()
        }
        taskViewModel.pendingCount.observe(this) {
            binding.textPendingTasks.text = it.toString()
            updateProgress()
        }
        taskViewModel.overdueCount.observe(this) {
            binding.textOverdueTasks.text = it.toString()
        }
        taskViewModel.completedToday.observe(this) {
            binding.textCompletedToday.text = it.toString()
        }
        taskViewModel.completedThisWeek.observe(this) {
            binding.textCompletedWeek.text = it.toString()
        }
        taskViewModel.completedPerDay.observe(this) { dayCounts ->
            updateChart(dayCounts)
        }
        binding.textFocusSessions.text =
            FocusSessionStore.totalSessions(this).toString()
    }

    private fun updateProgress() {
        val total = binding.textTotalTasks.text.toString().toIntOrNull() ?: 0
        val completed = binding.textCompletedTasks.text.toString().toIntOrNull() ?: 0
        if (total > 0) {
            val percent = completed * 100 / total
            binding.progressCompletion.progress = percent
            binding.textCompletionPercent.text = "$percent%"
        }
    }

    private fun updateChart(dayCounts: List<DayCount>) {
        val today = DateUtils.startOfToday()
        val data = Array(7) { i ->
            val dayStart = today - (6 - i) * 24 * 60 * 60 * 1000L
            val dayKey = dayStart / 86400000L
            val count = dayCounts.firstOrNull { it.day == dayKey }?.count ?: 0
            val label = SimpleDateFormat("EEE", Locale.getDefault()).format(dayStart)
            BarChartView.BarData(label, count)
        }
        binding.barChart.setData(data)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}