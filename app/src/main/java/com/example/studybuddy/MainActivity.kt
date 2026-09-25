package com.example.studybuddy

import android.Manifest
import android.app.ActivityOptions
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
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
import com.example.studybuddy.ui.TaskViewModel
import com.example.studybuddy.util.BackupHelper
import com.example.studybuddy.util.ExportImportHelper
import com.example.studybuddy.util.UpdateChecker
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), TaskAdapter.OnItemClickListener {

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
            if (!granted) {
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
        setupSwipeToDismiss()
        observeTasks()
        requestPermissions()
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

        val categoryAll = binding.chipGroupCategory.findViewById<Chip>(R.id.chip_category_all)
        categoryAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedCategory = null
                taskViewModel.setCategoryFilter(null)
            }
        }
        taskViewModel.categories.observe(this) { categories ->
            renderCategoryChips(categories)
        }

        binding.chipPriorityAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedPriority = null
                taskViewModel.setPriorityFilter(null)
            }
        }
        binding.chipPriorityHigh.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedPriority = Priority.HIGH
                taskViewModel.setPriorityFilter(Priority.HIGH)
            }
        }
        binding.chipPriorityMedium.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedPriority = Priority.MEDIUM
                taskViewModel.setPriorityFilter(Priority.MEDIUM)
            }
        }
        binding.chipPriorityLow.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedPriority = Priority.LOW
                taskViewModel.setPriorityFilter(Priority.LOW)
            }
        }

        taskViewModel.availableTags.observe(this) { tags ->
            renderTagChips(tags)
        }
    }

    private fun renderCategoryChips(categories: List<CategoryEntity>) {
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
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    selectedCategory = if (isChecked) category.id else null
                    taskViewModel.setCategoryFilter(selectedCategory)
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
            binding.textViewEmpty.isVisible = tasks.isNullOrEmpty()
            binding.recyclerViewTasks.isVisible = !tasks.isNullOrEmpty()
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
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
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
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(getString(R.string.about_message, BuildConfig.VERSION_NAME))
                    .setPositiveButton(R.string.ok, null)
                    .show()
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
}