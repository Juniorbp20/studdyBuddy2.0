package com.example.studybuddy

import android.Manifest
import android.app.ActivityOptions
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.studybuddy.adapter.TaskAdapter
import com.example.studybuddy.databinding.ActivityMainBinding
import com.example.studybuddy.model.CategoryEntity
import com.example.studybuddy.model.Priority
import com.example.studybuddy.model.Task
import com.example.studybuddy.model.categoryIconRes
import com.example.studybuddy.model.displayName
import com.example.studybuddy.notification.AlarmManagerHelper
import com.example.studybuddy.notification.DailyOverdueWorker
import com.example.studybuddy.notification.NotificationHelper
import com.example.studybuddy.ui.DashboardSummaryFragment
import com.example.studybuddy.ui.TaskViewModel
import com.example.studybuddy.util.BackupHelper
import com.example.studybuddy.util.ExportImportHelper
import com.example.studybuddy.util.StudyGoalManager
import com.example.studybuddy.util.SyncManager
import com.example.studybuddy.util.SyncState
import com.example.studybuddy.util.UpdateChecker
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), TaskAdapter.OnItemClickListener,
    DashboardSummaryFragment.DashboardNavigationListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var adapter: TaskAdapter
    private lateinit var alarmHelper: AlarmManagerHelper

    private var lastTasks: List<Task> = emptyList()
    private var selectedCategory: Int? = null
    private var selectedPriority: Int? = null
    private var selectedTag: String? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                checkDailyGoalReminder()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.notification_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                val tasks = taskViewModel.allTasks.value ?: emptyList()
                val categories = taskViewModel.categories.value ?: emptyList()
                val success = ExportImportHelper.writeToUri(
                    this, uri, ExportImportHelper.exportToJson(tasks, categories)
                )
                showExportResult(success, tasks.size)
            }
        }

    private val exportCsvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                val tasks = taskViewModel.allTasks.value ?: emptyList()
                val success = ExportImportHelper.writeToUri(
                    this, uri, ExportImportHelper.exportToCsv(tasks)
                )
                showExportResult(success, tasks.size)
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val json = ExportImportHelper.readFromUri(this, uri)
                if (json != null) {
                    val result = ExportImportHelper.importFromJson(json)
                    lifecycleScope.launch {
                        val idMap = mutableMapOf<Int, Int>()
                        result.categories.forEach { category ->
                            idMap[category.id] = taskViewModel.upsertCategoryNow(category)
                        }
                        result.tasks.forEach { task ->
                            val mapped = idMap[task.categoryId]?.let {
                                task.copy(categoryId = it)
                            } ?: task
                            taskViewModel.insert(mapped) { id ->
                                if (mapped.reminderEnabled &&
                                    mapped.dueDate > System.currentTimeMillis()
                                ) {
                                    alarmHelper.setAlarm(
                                        id.toInt(), mapped.title, mapped.description,
                                        mapped.dueDate, mapped.repeatInterval
                                    )
                                }
                            }
                        }
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.import_success, result.tasks.size),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(this, R.string.import_error, Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.main_title)

        alarmHelper = AlarmManagerHelper(this)
        taskViewModel = ViewModelProvider(this)[TaskViewModel::class.java]

        BackupHelper.maybeBackup(this)
        scheduleOverdueWorker()

        setupRecyclerView()
        setupFilters()
        setupFab()
        setupHeroDashboard()
        setupBottomNavigation()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DashboardSummaryFragment.newInstance())
                .commit()
        }
        showDashboardTab()

        setupSwipeToDismiss()
        observeTasks()
        requestPermissions()
        checkDailyGoalReminder()
        setupSyncMonitoring()
    }

    private fun setupSyncMonitoring() {
        val syncManager = SyncManager.getInstance(this)
        binding.btnSyncBannerAction.setOnClickListener {
            lifecycleScope.launch {
                val ok = syncManager.performSync(this@MainActivity, force = true)
                if (ok) {
                    Toast.makeText(this@MainActivity, R.string.sync_status_online, Toast.LENGTH_SHORT).show()
                }
            }
        }

        lifecycleScope.launch {
            syncManager.syncState.collect { state ->
                updateSyncBanner(state)
            }
        }

        lifecycleScope.launch {
            syncManager.syncEvents.collect { event ->
                when (event) {
                    is SyncManager.SyncEvent.NetworkRestored -> {
                        Snackbar.make(binding.root, R.string.sync_network_restored, Snackbar.LENGTH_SHORT).show()
                    }
                    is SyncManager.SyncEvent.SyncCompleted -> {
                        if (event.syncedCount > 0) {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.sync_completed_toast, event.syncedCount),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun updateSyncBanner(state: SyncState) {
        if (!state.isOnline) {
            binding.layoutSyncBanner.visibility = View.VISIBLE
            binding.iconSyncBanner.setImageResource(R.drawable.ic_cloud_off)
            binding.iconSyncBanner.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            )
            binding.textSyncBanner.text = if (state.pendingCount > 0) {
                getString(R.string.sync_status_offline) + " (" + getString(R.string.sync_pending_count, state.pendingCount) + ")"
            } else {
                getString(R.string.sync_status_offline)
            }
            binding.btnSyncBannerAction.visibility = View.GONE
        } else if (state.isSyncing) {
            binding.layoutSyncBanner.visibility = View.VISIBLE
            binding.iconSyncBanner.setImageResource(R.drawable.ic_sync)
            binding.iconSyncBanner.imageTintList = ColorStateList.valueOf(
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
            )
            binding.textSyncBanner.text = getString(R.string.sync_status_syncing)
            binding.btnSyncBannerAction.visibility = View.GONE
        } else if (state.pendingCount > 0) {
            binding.layoutSyncBanner.visibility = View.VISIBLE
            binding.iconSyncBanner.setImageResource(R.drawable.ic_cloud_done)
            binding.iconSyncBanner.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.holo_blue_dark)
            )
            binding.textSyncBanner.text = getString(R.string.sync_pending_count, state.pendingCount)
            binding.btnSyncBannerAction.visibility = View.VISIBLE
        } else {
            binding.layoutSyncBanner.visibility = View.GONE
        }
    }

    private fun scheduleOverdueWorker() {
        val request = PeriodicWorkRequestBuilder<DailyOverdueWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_overdue_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun setupRecyclerView() {
        adapter = TaskAdapter(this)
        binding.recyclerViewTasks.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewTasks.adapter = adapter
        binding.recyclerViewTasks.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
        val animation = android.view.animation.LayoutAnimationController(
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.item_fall_down),
            0.3f
        )
        binding.recyclerViewTasks.layoutAnimation = animation
    }

    private fun setupFilters() {
        binding.editTextSearch.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    taskViewModel.setSearchQuery(s?.toString())
                }
            }
        )

        binding.chipGroupCategory.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            if (checkedId == null || checkedId == R.id.chip_category_all) {
                selectedCategory = null
                if (checkedId == null) {
                    binding.chipCategoryAll.isChecked = true
                }
                taskViewModel.setCategoryFilter(null)
            } else {
                val chip = group.findViewById<Chip>(checkedId)
                val catId = chip?.tag as? Int
                selectedCategory = catId
                taskViewModel.setCategoryFilter(catId)
            }
        }
        taskViewModel.categories.observe(this) { categories ->
            renderCategoryChips(categories)
        }

        binding.chipGroupPriority.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            when (checkedId) {
                R.id.chip_priority_high -> {
                    selectedPriority = Priority.HIGH
                    taskViewModel.setPriorityFilter(Priority.HIGH)
                }
                R.id.chip_priority_medium -> {
                    selectedPriority = Priority.MEDIUM
                    taskViewModel.setPriorityFilter(Priority.MEDIUM)
                }
                R.id.chip_priority_low -> {
                    selectedPriority = Priority.LOW
                    taskViewModel.setPriorityFilter(Priority.LOW)
                }
                else -> {
                    selectedPriority = null
                    if (checkedId == null) {
                        binding.chipPriorityAll.isChecked = true
                    }
                    taskViewModel.setPriorityFilter(null)
                }
            }
        }

        taskViewModel.availableTags.observe(this) { tags ->
            renderTagChips(tags)
        }
    }

    private fun renderCategoryChips(categories: List<CategoryEntity>) {
        val density = resources.displayMetrics.density
        val existing = mutableMapOf<Int, Chip>()
        for (i in 0 until binding.chipGroupCategory.childCount) {
            val chip = binding.chipGroupCategory.getChildAt(i) as? Chip ?: continue
            val id = chip.tag as? Int ?: continue
            existing[id] = chip
        }
        val seen = mutableSetOf<Int>()
        categories.forEach { category ->
            seen.add(category.id)
            val chip = existing[category.id]
            if (chip != null) {
                if (chip.text != category.displayName(this)) {
                    chip.text = category.displayName(this)
                }
                return@forEach
            }
            val iconRes = category.icon.categoryIconRes() ?: R.drawable.ic_cat_star
            val newChip = Chip(this).apply {
                tag = category.id
                text = category.displayName(this@MainActivity)
                chipIcon = ContextCompat.getDrawable(this@MainActivity, iconRes)
                chipIconTint = ColorStateList.valueOf(category.color)
                chipStrokeColor = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(category.color, ColorUtils.setAlphaComponent(category.color, 110))
                )
                chipStrokeWidth = density * 1.5f
                isCheckable = true
                if (selectedCategory == category.id) {
                    isChecked = true
                }
            }
            binding.chipGroupCategory.addView(newChip)
        }
        existing.forEach { (id, chip) ->
            if (id !in seen) {
                binding.chipGroupCategory.removeView(chip)
            }
        }
    }

    private fun renderTagChips(tags: List<String>) {
        val currentTags = mutableListOf<String>()
        for (i in 0 until binding.chipGroupTags.childCount) {
            val chip = binding.chipGroupTags.getChildAt(i) as? Chip ?: continue
            currentTags.add(chip.text.toString())
        }
        tags.forEach { tag ->
            if (tag !in currentTags) {
                val chip = Chip(this).apply {
                    text = tag
                    isCheckable = true
                    setOnCheckedChangeListener { _, isChecked ->
                        selectedTag = if (isChecked) tag else null
                        taskViewModel.setTagFilter(selectedTag)
                    }
                }
                binding.chipGroupTags.addView(chip)
            }
        }
        binding.chipGroupTags.isVisible = tags.isNotEmpty()
    }

    private fun setupFab() {
        binding.fabAddTask.setOnClickListener {
            val options = ActivityOptions.makeCustomAnimation(
                this, android.R.anim.fade_in, android.R.anim.fade_out
            )
            startActivity(
                Intent(this, AddTaskActivity::class.java),
                options.toBundle()
            )
        }

        binding.recyclerViewTasks.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 4 && binding.fabAddTask.isExtended) {
                    binding.fabAddTask.shrink()
                } else if (dy < -4 && !binding.fabAddTask.isExtended) {
                    binding.fabAddTask.extend()
                }
            }
        })
    }

    private fun setupHeroDashboard() {
        binding.buttonHeroPomodoro.setOnClickListener {
            startActivity(Intent(this, FocusActivity::class.java))
        }
        binding.buttonEmptyAdd.setOnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }

        taskViewModel.totalCount.observe(this) { total ->
            val completed = taskViewModel.completedCount.value ?: 0
            updateHeroProgress(total, completed)
        }
        taskViewModel.completedCount.observe(this) { completed ->
            val total = taskViewModel.totalCount.value ?: 0
            updateHeroProgress(total, completed)
        }
    }

    private fun updateHeroProgress(total: Int, completed: Int) {
        if (total > 0) {
            val percentage = (completed * 100) / total
            binding.progressHeroTasks.max = total
            binding.progressHeroTasks.progress = completed
            binding.textHeroProgress.text = getString(
                R.string.dashboard_progress_summary,
                completed,
                total,
                percentage
            )
        } else {
            binding.progressHeroTasks.max = 1
            binding.progressHeroTasks.progress = 0
            binding.textHeroProgress.text = getString(R.string.dashboard_progress_title)
        }
    }

    override fun onNavigateToTasksList() {
        binding.bottomNavigation.selectedItemId = R.id.nav_tasks
    }

    private fun showDashboardTab() {
        binding.fragmentContainer.visibility = View.VISIBLE
        binding.layoutTasksHeader.visibility = View.GONE
        binding.layoutTasksView.visibility = View.GONE
        supportActionBar?.title = getString(R.string.dashboard_title)
    }

    private fun showTasksTab() {
        binding.fragmentContainer.visibility = View.GONE
        binding.layoutTasksHeader.visibility = View.VISIBLE
        binding.layoutTasksView.visibility = View.VISIBLE
        supportActionBar?.title = getString(R.string.main_title)
        binding.recyclerViewTasks.scheduleLayoutAnimation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    showDashboardTab()
                    true
                }
                R.id.nav_tasks -> {
                    showTasksTab()
                    binding.recyclerViewTasks.smoothScrollToPosition(0)
                    true
                }
                R.id.nav_focus -> {
                    startActivity(Intent(this, FocusActivity::class.java))
                    false
                }
                R.id.nav_calendar -> {
                    startActivity(Intent(this, CalendarActivity::class.java))
                    false
                }
                R.id.nav_statistics -> {
                    startActivity(Intent(this, StatisticsActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (binding.bottomNavigation.selectedItemId != R.id.nav_dashboard &&
            binding.bottomNavigation.selectedItemId != R.id.nav_tasks) {
            binding.bottomNavigation.selectedItemId = R.id.nav_dashboard
        }
        if (StudyGoalManager.isGoalMetToday(this)) {
            NotificationHelper(this).cancelDailyGoalReminderNotification()
        }
    }

    private fun checkDailyGoalReminder() {
        if (!StudyGoalManager.isGoalMetToday(this)) {
            if (StudyGoalManager.shouldRemindOnAppOpen(this)) {
                val completed = StudyGoalManager.getCompletedMinutesToday(this)
                val target = StudyGoalManager.getTargetMinutes(this)
                val remaining = StudyGoalManager.getRemainingMinutesToday(this)

                NotificationHelper(this).showDailyGoalReminderNotification(
                    completedMinutes = completed,
                    targetMinutes = target,
                    remainingMinutes = remaining
                )
                StudyGoalManager.recordReminderSent(this)
            }
        } else {
            NotificationHelper(this).cancelDailyGoalReminderNotification()
        }
    }

    private fun setupSwipeToDismiss() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val task = adapter.currentList[viewHolder.bindingAdapterPosition]
                if (direction == ItemTouchHelper.LEFT) {
                    deleteTask(task)
                } else {
                    toggleTaskCompleted(task)
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerViewTasks)
    }

    private fun observeTasks() {
        taskViewModel.filteredTasks.observe(this) { tasks ->
            lastTasks = tasks
            adapter.submitList(tasks)
            val isEmpty = tasks.isNullOrEmpty()
            binding.emptyState.isVisible = isEmpty
            binding.recyclerViewTasks.isVisible = !isEmpty
            if (isEmpty) {
                if (selectedCategory != null || selectedPriority != null || !selectedTag.isNullOrBlank()) {
                    binding.textViewEmpty.text = getString(R.string.no_results)
                } else {
                    binding.textViewEmpty.text = getString(R.string.empty_title)
                }
            }
            binding.recyclerViewTasks.scheduleLayoutAnimation()
        }
        taskViewModel.categories.observe(this) { categories ->
            adapter.updateCategories(categories)
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmHelper.canScheduleExactAlarms()) {
            Snackbar.make(
                binding.root,
                getString(R.string.exact_alarm_snackbar),
                Snackbar.LENGTH_LONG
            )
                .setAction(getString(R.string.exact_alarm_action)) {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    )
                }
                .show()
        }
    }

    override fun onItemClick(task: Task) {
        val intent = Intent(this, TaskDetailActivity::class.java)
        intent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id)
        startActivity(intent)
    }

    override fun onTaskStatusChanged(task: Task, isCompleted: Boolean) {
        taskViewModel.update(
            task.copy(
                isCompleted = isCompleted,
                completedAt = if (isCompleted) System.currentTimeMillis() else 0L
            )
        )
        if (isCompleted) {
            alarmHelper.cancelAlarm(task.id)
        }
    }

    override fun onTaskDelete(task: Task) {
        deleteTask(task)
    }

    private fun toggleTaskCompleted(task: Task) {
        onTaskStatusChanged(task, !task.isCompleted)
    }

    private fun deleteTask(task: Task) {
        taskViewModel.delete(task)
        alarmHelper.cancelAlarm(task.id)
        Snackbar.make(
            binding.root,
            getString(R.string.task_deleted),
            Snackbar.LENGTH_LONG
        ).setAction(getString(R.string.undo)) {
            taskViewModel.insert(task) { id ->
                if (task.reminderEnabled && task.dueDate > System.currentTimeMillis()) {
                    alarmHelper.setAlarm(
                        id.toInt(), task.title, task.description, task.dueDate, task.repeatInterval
                    )
                }
            }
        }.show()
    }

    private fun checkForUpdates() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_checking_title)
            .setMessage(R.string.update_checking)
            .setCancelable(false)
            .show()
        lifecycleScope.launch {
            val info = UpdateChecker.checkLatest(this@MainActivity)
            dialog.dismiss()
            if (info == null || info.apkUrl.isBlank()) {
                Toast.makeText(
                    this@MainActivity, R.string.update_check_error, Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            if (!UpdateChecker.isUpdateAvailable(BuildConfig.VERSION_CODE, info)) {
                Toast.makeText(
                    this@MainActivity, R.string.update_latest, Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.update_available_title)
                .setMessage(
                    getString(
                        R.string.update_available_message,
                        info.versionName,
                        BuildConfig.VERSION_NAME
                    )
                )
                .setPositiveButton(R.string.update_download) { _, _ ->
                    downloadAndInstall(info.apkUrl)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun downloadAndInstall(url: String) {
        val progressDialog = ProgressDialog(this).apply {
            setTitle(R.string.update_downloading)
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
        }
        progressDialog.show()
        lifecycleScope.launch {
            val file = UpdateChecker.downloadApk(this@MainActivity, url) { downloaded, total ->
                if (total > 0) {
                    progressDialog.max = total
                    progressDialog.progress = downloaded
                } else {
                    progressDialog.isIndeterminate = true
                }
            }
            progressDialog.dismiss()
            if (file == null) {
                Toast.makeText(
                    this@MainActivity, R.string.update_download_error, Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            if (!UpdateChecker.canInstall(this@MainActivity)) {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.update_install_permission_title)
                    .setMessage(R.string.update_install_permission_message)
                    .setPositiveButton(R.string.update_install_permission_action) { _, _ ->
                        UpdateChecker.openInstallPermissionSettings(this@MainActivity)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                return@launch
            }
            UpdateChecker.installApk(this@MainActivity, file)
        }
    }

    private fun showExportResult(success: Boolean, count: Int) {
        Toast.makeText(
            this,
            if (success) getString(R.string.export_success, count) else getString(R.string.export_error),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        tintModernMenuIcon(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        tintModernMenuIcon(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun tintModernMenuIcon(menu: android.view.Menu) {
        val moreItem = menu.findItem(R.id.action_modern_menu)
        moreItem?.icon?.let { icon ->
            val tintColor = com.google.android.material.color.MaterialColors.getColor(
                binding.toolbar,
                com.google.android.material.R.attr.colorOnSurface
            )
            androidx.core.graphics.drawable.DrawableCompat.setTint(icon, tintColor)
        }
    }

    override fun openOptionsMenu() {
        showModernMenuBottomSheet()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_modern_menu -> {
                showModernMenuBottomSheet()
                true
            }
            R.id.action_statistics -> {
                startActivity(Intent(this, StatisticsActivity::class.java))
                true
            }
            R.id.action_calendar -> {
                startActivity(Intent(this, CalendarActivity::class.java))
                true
            }
            R.id.action_focus -> {
                startActivity(Intent(this, FocusActivity::class.java))
                true
            }
            R.id.action_export -> {
                exportLauncher.launch(ExportImportHelper.defaultExportFileName())
                true
            }
            R.id.action_export_csv -> {
                exportCsvLauncher.launch("studybuddy_tasks.csv")
                true
            }
            R.id.action_import -> {
                importLauncher.launch(arrayOf("application/json", "text/*"))
                true
            }
            R.id.action_check_update -> {
                checkForUpdates()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            R.id.action_theme_system -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                true
            }
            R.id.action_theme_light -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                true
            }
            R.id.action_theme_dark -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showModernMenuBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_modern_menu, null)
        dialog.setContentView(sheetView)

        // Version Chip
        val versionText = sheetView.findViewById<TextView>(R.id.text_app_version_chip)
        versionText?.text = "v${BuildConfig.VERSION_NAME}"

        // Daily Goal Description
        val goalDesc = sheetView.findViewById<TextView>(R.id.text_menu_goal_desc)
        val targetHours = StudyGoalManager.getTargetHours(this)
        goalDesc?.text = getString(
            R.string.modern_menu_goal_desc,
            String.format(Locale.getDefault(), "%.1f", targetHours)
        )

        // Productivity & Focus Actions
        sheetView.findViewById<View>(R.id.menu_item_statistics)?.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, StatisticsActivity::class.java))
        }

        sheetView.findViewById<View>(R.id.menu_item_calendar)?.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, CalendarActivity::class.java))
        }

        sheetView.findViewById<View>(R.id.menu_item_focus)?.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, FocusActivity::class.java))
        }

        sheetView.findViewById<View>(R.id.menu_item_daily_goal)?.setOnClickListener {
            dialog.dismiss()
            showGoalSettingDialog()
        }

        // Backup & Data Actions
        sheetView.findViewById<View>(R.id.menu_item_export)?.setOnClickListener {
            dialog.dismiss()
            exportLauncher.launch(ExportImportHelper.defaultExportFileName())
        }

        sheetView.findViewById<View>(R.id.menu_item_export_csv)?.setOnClickListener {
            dialog.dismiss()
            exportCsvLauncher.launch("studybuddy_tasks.csv")
        }

        sheetView.findViewById<View>(R.id.menu_item_import)?.setOnClickListener {
            dialog.dismiss()
            importLauncher.launch(arrayOf("application/json", "text/*"))
        }

        // Appearance / Theme Toggle
        val themeLabel = sheetView.findViewById<TextView>(R.id.text_current_theme_label)
        val toggleGroup = sheetView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggle_theme_group)

        val currentMode = AppCompatDelegate.getDefaultNightMode()
        when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> {
                themeLabel?.setText(R.string.menu_theme_light)
                toggleGroup?.check(R.id.btn_theme_light)
            }
            AppCompatDelegate.MODE_NIGHT_YES -> {
                themeLabel?.setText(R.string.menu_theme_dark)
                toggleGroup?.check(R.id.btn_theme_dark)
            }
            else -> {
                themeLabel?.setText(R.string.menu_theme_system)
                toggleGroup?.check(R.id.btn_theme_system)
            }
        }

        toggleGroup?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_theme_system -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        dialog.dismiss()
                    }
                    R.id.btn_theme_light -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        dialog.dismiss()
                    }
                    R.id.btn_theme_dark -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        dialog.dismiss()
                    }
                }
            }
        }

        // App & Support Actions
        sheetView.findViewById<View>(R.id.menu_item_check_update)?.setOnClickListener {
            dialog.dismiss()
            checkForUpdates()
        }

        sheetView.findViewById<View>(R.id.menu_item_about)?.setOnClickListener {
            dialog.dismiss()
            showAboutDialog()
        }

        // Sync & Network Actions & Display
        val syncTitle = sheetView.findViewById<TextView>(R.id.text_sync_status_title)
        val syncSubtitle = sheetView.findViewById<TextView>(R.id.text_sync_status_subtitle)
        val syncIcon = sheetView.findViewById<android.widget.ImageView>(R.id.img_sync_icon)
        val btnSyncNow = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_sync_now)

        fun updateSheetSyncUI(state: SyncState) {
            if (!state.isOnline) {
                syncTitle?.setText(R.string.sync_status_offline)
                syncSubtitle?.text = if (state.pendingCount > 0) {
                    getString(R.string.sync_pending_count, state.pendingCount)
                } else {
                    getString(R.string.sync_offline_mode_banner)
                }
                syncIcon?.setImageResource(R.drawable.ic_cloud_off)
                syncIcon?.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                )
                btnSyncNow?.isEnabled = false
            } else if (state.isSyncing) {
                syncTitle?.setText(R.string.sync_status_syncing)
                syncSubtitle?.text = getString(R.string.sync_pending_count, state.pendingCount)
                syncIcon?.setImageResource(R.drawable.ic_sync)
                syncIcon?.imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
                )
                btnSyncNow?.isEnabled = false
            } else {
                syncTitle?.setText(R.string.sync_status_online)
                val lastSync = state.lastSyncMillis
                syncSubtitle?.text = if (lastSync > 0) {
                    val formatted = SimpleDateFormat("HH:mm · dd MMM", Locale.getDefault()).format(Date(lastSync))
                    getString(R.string.sync_last_time, formatted)
                } else {
                    getString(R.string.sync_just_now)
                }
                syncIcon?.setImageResource(R.drawable.ic_cloud_done)
                syncIcon?.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, android.R.color.holo_green_dark)
                )
                btnSyncNow?.isEnabled = true
            }
        }

        updateSheetSyncUI(SyncManager.getInstance(this).syncState.value)

        btnSyncNow?.setOnClickListener {
            lifecycleScope.launch {
                btnSyncNow.isEnabled = false
                syncTitle?.setText(R.string.sync_status_syncing)
                val ok = SyncManager.getInstance(this@MainActivity).performSync(this@MainActivity, force = true)
                updateSheetSyncUI(SyncManager.getInstance(this@MainActivity).syncState.value)
                Toast.makeText(
                    this@MainActivity,
                    if (ok) R.string.sync_completed_toast else R.string.sync_status_offline,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        sheetView.findViewById<View>(R.id.menu_item_sync)?.setOnClickListener {
            val isOnline = SyncManager.getInstance(this).syncState.value.isOnline
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sync_title)
                .setIcon(if (isOnline) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off)
                .setMessage(
                    (if (isOnline) getString(R.string.sync_status_online) else getString(R.string.sync_status_offline)) +
                    "\n\n" + getString(R.string.sync_offline_detail)
                )
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton(R.string.sync_now) { _, _ ->
                    lifecycleScope.launch {
                        SyncManager.getInstance(this@MainActivity).performSync(this@MainActivity, force = true)
                    }
                }
                .show()
        }

        dialog.show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setIcon(R.drawable.ic_cat_book)
            .setMessage(getString(R.string.about_message, BuildConfig.VERSION_NAME))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showGoalSettingDialog() {
        val currentTarget = StudyGoalManager.getTargetHours(this)
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

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.daily_goal_dialog_title)
            .setSingleChoiceItems(options, selectedIndex) { _, which ->
                chosenIndex = which
            }
            .setPositiveButton(R.string.save) { _, _ ->
                val newTarget = values[chosenIndex]
                StudyGoalManager.setTargetHours(this, newTarget)
                val targetStr = String.format(Locale.getDefault(), "%.1f", newTarget)
                Toast.makeText(
                    this,
                    getString(R.string.daily_goal_saved, targetStr),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}