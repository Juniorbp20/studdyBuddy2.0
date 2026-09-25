package com.example.studybuddy.ui

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studybuddy.AddTaskActivity
import com.example.studybuddy.FocusActivity
import com.example.studybuddy.R
import com.example.studybuddy.TaskDetailActivity
import com.example.studybuddy.adapter.UpcomingTaskAdapter
import com.example.studybuddy.databinding.FragmentDashboardSummaryBinding
import com.example.studybuddy.model.Task
import com.example.studybuddy.util.FocusSessionStore
import com.example.studybuddy.util.StudyGoalManager
import com.example.studybuddy.util.StudyTrendHelper
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardSummaryFragment : Fragment(), UpcomingTaskAdapter.OnUpcomingTaskClickListener {

    private var _binding: FragmentDashboardSummaryBinding? = null
    private val binding get() = _binding!!

    private lateinit var taskViewModel: TaskViewModel
    private lateinit var upcomingAdapter: UpcomingTaskAdapter

    interface DashboardNavigationListener {
        fun onNavigateToTasksList()
    }

    private var navigationListener: DashboardNavigationListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is DashboardNavigationListener) {
            navigationListener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taskViewModel = ViewModelProvider(requireActivity())[TaskViewModel::class.java]

        setupHeader()
        setupDailyGoalCard()
        setupRechartsDashboard()
        setupQuickStartCard()
        setupUpcomingRecyclerView()
        setupListeners()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        updateFocusStats()
        updateDailyGoalDisplay()
        sendStudyDataToRecharts()
    }

    private fun setupDailyGoalCard() {
        binding.btnGoalStartStudy.setOnClickListener {
            startPomodoroQuickly()
        }
        binding.btnEditGoal.setOnClickListener {
            showGoalSettingDialog()
        }
        updateDailyGoalDisplay()
    }

    private fun updateDailyGoalDisplay() {
        val context = context ?: return
        val targetHours = StudyGoalManager.getTargetHours(context)
        val targetMinutes = StudyGoalManager.getTargetMinutes(context)
        val completedMinutes = StudyGoalManager.getCompletedMinutesToday(context)
        val remainingMinutes = StudyGoalManager.getRemainingMinutesToday(context)
        val percent = StudyGoalManager.getGoalProgressPercent(context)
        val isMet = StudyGoalManager.isGoalMetToday(context)

        val targetHoursStr = String.format(Locale.getDefault(), "%.1f", targetHours)
        val completedDurationStr = StudyGoalManager.formatDuration(completedMinutes)
        val remainingDurationStr = StudyGoalManager.formatDuration(remainingMinutes)

        binding.progressDailyGoal.max = 100
        binding.progressDailyGoal.progress = percent

        val primaryColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        val successColor = Color.parseColor("#10B981")
        val warningColor = Color.parseColor("#F59E0B")

        if (isMet) {
            binding.imageGoalStatus.setImageResource(R.drawable.ic_check_circle)
            binding.imageGoalStatus.imageTintList = ColorStateList.valueOf(successColor)
            binding.textGoalTitle.setText(R.string.daily_goal_card_met_title)
            binding.textGoalSubtitle.text = getString(
                R.string.daily_goal_card_met_desc,
                completedDurationStr,
                targetHoursStr
            )
            binding.progressDailyGoal.setIndicatorColor(successColor)
            binding.textGoalProgressBadge.setTextColor(successColor)
            binding.textGoalProgressBadge.text = "$percent% completado ✓"
            binding.btnGoalStartStudy.text = "Seguir estudiando ⏱️"
        } else {
            binding.imageGoalStatus.setImageResource(R.drawable.ic_target_goal)
            val iconColor = if (percent > 0) primaryColor else warningColor
            binding.imageGoalStatus.imageTintList = ColorStateList.valueOf(iconColor)
            binding.textGoalTitle.setText(R.string.daily_goal_card_title)
            binding.textGoalSubtitle.text = if (completedMinutes == 0) {
                getString(R.string.notification_goal_body_not_started, targetHoursStr)
            } else {
                getString(
                    R.string.daily_goal_card_progress,
                    completedDurationStr,
                    targetHoursStr,
                    percent,
                    remainingDurationStr
                )
            }
            binding.progressDailyGoal.setIndicatorColor(primaryColor)
            binding.textGoalProgressBadge.setTextColor(primaryColor)
            binding.textGoalProgressBadge.text = "$percent% completado"
            binding.btnGoalStartStudy.setText(R.string.dashboard_quick_start_btn)
        }
    }

    private fun showGoalSettingDialog() {
        val context = context ?: return
        val currentTarget = StudyGoalManager.getTargetHours(context)
        val options = arrayOf(
            "0.5 horas (30 min)",
            "1.0 hora (60 min)",
            "1.5 horas (90 min)",
            "2.0 horas (120 min) · Recomendado",
            "2.5 horas (150 min)",
            "3.0 horas (180 min)",
            "4.0 horas (240 min)"
        )
        val values = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)

        // Find closest selected index
        var selectedIndex = 3
        var minDiff = Float.MAX_VALUE
        for (i in values.indices) {
            val diff = Math.abs(values[i] - currentTarget)
            if (diff < minDiff) {
                minDiff = diff
                selectedIndex = i
            }
        }

        var chosenIndex = selectedIndex

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.daily_goal_dialog_title)
            .setSingleChoiceItems(options, selectedIndex) { _, which ->
                chosenIndex = which
            }
            .setPositiveButton(R.string.save) { _, _ ->
                val newTarget = values[chosenIndex]
                StudyGoalManager.setTargetHours(context, newTarget)
                updateDailyGoalDisplay()
                val targetStr = String.format(Locale.getDefault(), "%.1f", newTarget)
                Toast.makeText(
                    context,
                    getString(R.string.daily_goal_saved, targetStr),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupHeader() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val greetingRes = when (hour) {
            in 5..11 -> R.string.dashboard_greeting_morning
            in 12..18 -> R.string.dashboard_greeting_afternoon
            else -> R.string.dashboard_greeting_evening
        }
        binding.textDashboardGreeting.setText(greetingRes)

        val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault())
        val formattedDate = dateFormat.format(Date()).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        binding.textDashboardDate.text = formattedDate
    }

    private fun setupQuickStartCard() {
        binding.btnQuickStartPomodoro.setOnClickListener {
            startPomodoroQuickly()
        }
        binding.cardQuickStartPomodoro.setOnClickListener {
            startPomodoroQuickly()
        }
    }

    private fun startPomodoroQuickly() {
        val intent = Intent(requireContext(), FocusActivity::class.java).apply {
            putExtra(FocusActivity.EXTRA_START_IMMEDIATELY, true)
        }
        val options = ActivityOptions.makeCustomAnimation(
            requireContext(), android.R.anim.fade_in, android.R.anim.fade_out
        )
        startActivity(intent, options.toBundle())
    }

    private fun setupUpcomingRecyclerView() {
        upcomingAdapter = UpcomingTaskAdapter(this)
        binding.recyclerUpcomingTasks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = upcomingAdapter
        }
    }

    private fun setupListeners() {
        binding.btnViewAllTasks.setOnClickListener {
            navigationListener?.onNavigateToTasksList()
        }

        binding.btnCreateUpcomingTask.setOnClickListener {
            startActivity(Intent(requireContext(), AddTaskActivity::class.java))
        }
    }

    private fun observeData() {
        // Daily focus stats
        updateFocusStats()

        // Tasks completed today
        taskViewModel.completedToday.observe(viewLifecycleOwner) { completedToday ->
            binding.textStatCompletedTasks.text = completedToday.toString()
            updateDailyProgress(completedToday, taskViewModel.pendingCount.value ?: 0)
        }

        // Pending tasks
        taskViewModel.pendingCount.observe(viewLifecycleOwner) { pendingCount ->
            binding.textStatPendingTasks.text = pendingCount.toString()
            updateDailyProgress(taskViewModel.completedToday.value ?: 0, pendingCount)
        }

        // Upcoming tasks
        taskViewModel.upcomingTasks.observe(viewLifecycleOwner) { tasks ->
            val hasTasks = !tasks.isNullOrEmpty()
            binding.recyclerUpcomingTasks.visibility = if (hasTasks) View.VISIBLE else View.GONE
            binding.layoutUpcomingEmpty.visibility = if (hasTasks) View.GONE else View.VISIBLE
            upcomingAdapter.submitList(tasks)
        }

        // Category updates for styling
        taskViewModel.categories.observe(viewLifecycleOwner) { categories ->
            upcomingAdapter.updateCategories(categories)
        }
    }

    private fun updateFocusStats() {
        val context = context ?: return
        val sessionsToday = FocusSessionStore.sessionsToday(context)
        val minutesToday = FocusSessionStore.focusMinutesToday(context)

        binding.textStatPomodoros.text = sessionsToday.toString()
        binding.textStatStudyTime.text = "$minutesToday min"
        binding.textPomodoroSessionsIndicator.text = getString(
            R.string.dashboard_quick_start_sessions_today,
            sessionsToday
        )
    }

    private fun updateDailyProgress(completedToday: Int, pending: Int) {
        val totalToday = completedToday + pending
        if (totalToday > 0) {
            val percent = (completedToday * 100) / totalToday
            binding.progressDashboardTasks.max = 100
            binding.progressDashboardTasks.progress = percent
            binding.textDashboardProgressPercent.text = "$percent%"
        } else {
            binding.progressDashboardTasks.max = 100
            binding.progressDashboardTasks.progress = 0
            binding.textDashboardProgressPercent.text = "0%"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupRechartsDashboard() {
        val webView = binding.webviewRechartsDashboard
        webView.setBackgroundColor(0)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true

        val isDark = isDarkTheme(requireContext())
        val url = "file:///android_asset/recharts/study_dashboard.html?dark=$isDark"

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                sendStudyDataToRecharts()
            }
        }
        webView.loadUrl(url)
    }

    private fun isDarkTheme(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    private fun sendStudyDataToRecharts() {
        val context = context ?: return
        val isDark = isDarkTheme(context)
        val json = StudyTrendHelper.getDashboardJson(context, isDark)
        binding.webviewRechartsDashboard.evaluateJavascript(
            "window.updateStudyData && window.updateStudyData($json);",
            null
        )
    }

    override fun onItemClick(task: Task) {
        val intent = Intent(requireContext(), TaskDetailActivity::class.java).apply {
            putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id)
        }
        startActivity(intent)
    }

    override fun onTaskCompleted(task: Task, isCompleted: Boolean) {
        val updated = task.copy(
            isCompleted = isCompleted,
            completedAt = if (isCompleted) System.currentTimeMillis() else 0L
        )
        taskViewModel.update(updated)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.webviewRechartsDashboard.stopLoading()
        _binding = null
    }

    companion object {
        fun newInstance() = DashboardSummaryFragment()
    }
}
