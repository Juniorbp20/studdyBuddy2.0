package com.example.studybuddy

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.studybuddy.databinding.ActivityAddTaskBinding
import com.example.studybuddy.model.CategoryEntity
import com.example.studybuddy.model.Priority
import com.example.studybuddy.model.RepeatInterval
import com.example.studybuddy.model.Task
import com.example.studybuddy.model.categoryIconRes
import com.example.studybuddy.model.displayName
import com.example.studybuddy.notification.AlarmManagerHelper
import com.example.studybuddy.ui.TaskViewModel
import com.example.studybuddy.util.DateUtils
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

class AddTaskActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
    }

    private lateinit var binding: ActivityAddTaskBinding
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var alarmHelper: AlarmManagerHelper

    private var taskId = -1
    private var isEditing = false
    private var selectedCategory = CategoryEntity.ID_GENERAL
    private var selectedPriority = Priority.MEDIUM
    private var selectedRepeat = RepeatInterval.NONE
    private var selectedDateCalendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        taskViewModel = ViewModelProvider(this)[TaskViewModel::class.java]
        alarmHelper = AlarmManagerHelper(this)

        setupCategoryChips()
        setupPriorityChips()
        setupRepeatChips()
        setupClickListeners()
        checkForEditIntent()
        updateDateAndTimeViews()
    }

    private fun setupCategoryChips() {
        binding.chipCategoryGeneral.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedCategory = CategoryEntity.ID_GENERAL
        }
        binding.chipCategoryStudy.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedCategory = CategoryEntity.ID_STUDY
        }
        binding.chipCategoryWork.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedCategory = CategoryEntity.ID_WORK
        }
        binding.chipCategoryPersonal.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedCategory = CategoryEntity.ID_PERSONAL
        }
        binding.chipCategoryNew.setOnClickListener { showCreateCategoryDialog() }
        taskViewModel.categories.observe(this) { categories ->
            renderCategoryChips(categories)
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
        categories.filter { it.id !in CategoryEntity.ID_GENERAL..CategoryEntity.ID_PERSONAL }
            .forEach { category ->
                seen.add(category.id)
                if (existing[category.id] != null) return@forEach
                val iconRes = category.icon.categoryIconRes() ?: R.drawable.ic_cat_star
                val chip = Chip(this).apply {
                    tag = category.id
                    text = category.displayName(this@AddTaskActivity)
                    chipIcon = ContextCompat.getDrawable(this@AddTaskActivity, iconRes)
                    chipIconTint = ColorStateList.valueOf(category.color)
                    isCheckable = true
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedCategory = category.id
                    }
                    setOnLongClickListener {
                        confirmDeleteCategory(category)
                        true
                    }
                }
                binding.chipGroupCategory.addView(chip)
            }
        existing.forEach { (id, chip) ->
            if (id !in seen) {
                binding.chipGroupCategory.removeView(chip)
            }
        }
    }

    private fun showCreateCategoryDialog() {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(
            R.layout.dialog_new_category, null
        ) as View
        val nameInput = dialogView.findViewById<EditText>(R.id.edit_text_category_name)
        val iconGroup = dialogView.findViewById<ChipGroup>(R.id.chip_group_category_icon)

        CategoryEntity.ICON_KEYS.forEachIndexed { index, iconKey ->
            val iconRes = iconKey.categoryIconRes() ?: return@forEachIndexed
            val chip = Chip(this).apply {
                text = " "
                chipIcon = ContextCompat.getDrawable(this@AddTaskActivity, iconRes)
                chipIconTint = ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#616161")
                )
                isCheckable = true
                isChecked = index == 0
            }
            chip.tag = iconKey
            iconGroup.addView(chip)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_category)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    nameInput.error = getString(R.string.category_name_required)
                    return@setPositiveButton
                }
                val icon = (iconGroup.checkedChipId.let {
                    iconGroup.findViewById<Chip>(it)?.tag as? String
                }) ?: CategoryEntity.DEFAULT_ICON
                val customCount = (taskViewModel.categories.value ?: emptyList())
                    .count { it.id !in CategoryEntity.ID_GENERAL..CategoryEntity.ID_PERSONAL }
                val color = CategoryEntity.CUSTOM_COLORS[
                    customCount % CategoryEntity.CUSTOM_COLORS.size
                ]
                taskViewModel.upsertCategory(
                    CategoryEntity(name = name, icon = icon, color = color)
                ) { id ->
                    selectCategoryChip(id)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteCategory(category: CategoryEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_category_title)
            .setMessage(getString(R.string.delete_category_message, category.displayName(this)))
            .setPositiveButton(R.string.delete) { _, _ ->
                taskViewModel.deleteCategory(category)
                selectedCategory = CategoryEntity.ID_GENERAL
                binding.chipCategoryGeneral.isChecked = true
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun selectCategoryChip(categoryId: Int) {
        for (i in 0 until binding.chipGroupCategory.childCount) {
            val chip = binding.chipGroupCategory.getChildAt(i) as? Chip ?: continue
            if (chip.tag == categoryId) {
                chip.isChecked = true
                selectedCategory = categoryId
                return
            }
        }
    }

    private fun setupPriorityChips() {
        binding.chipPriorityLow.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedPriority = Priority.LOW
        }
        binding.chipPriorityMedium.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedPriority = Priority.MEDIUM
        }
        binding.chipPriorityHigh.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedPriority = Priority.HIGH
        }
    }

    private fun setupRepeatChips() {
        binding.chipRepeatNone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedRepeat = RepeatInterval.NONE
        }
        binding.chipRepeatDaily.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedRepeat = RepeatInterval.DAILY
        }
        binding.chipRepeatWeekly.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedRepeat = RepeatInterval.WEEKLY
        }
    }

    private fun setupClickListeners() {
        binding.buttonSelectDate.setOnClickListener { showDatePickerDialog() }
        binding.buttonSelectTime.setOnClickListener { showTimePickerDialog() }
        binding.buttonSaveTask.setOnClickListener { saveTask() }
    }

    private fun checkForEditIntent() {
        taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        isEditing = taskId != -1

        if (isEditing) {
            binding.textViewAddTitle.text = getString(R.string.edit_task_title)
            binding.buttonSaveTask.text = getString(R.string.update_task)
            taskViewModel.allTasks.observe(this) { tasks ->
                val task = tasks.firstOrNull { it.id == taskId } ?: return@observe
                binding.editTextTitle.setText(task.title)
                binding.editTextDescription.setText(task.description)
                binding.editTextTags.setText(task.tags)
                selectedCategory = task.categoryId
                selectedPriority = task.priority
                selectedRepeat = task.repeatInterval
                selectedDateCalendar.timeInMillis = task.dueDate
                binding.switchReminder.isChecked = task.reminderEnabled
                binding.switchReminder.isEnabled = task.reminderEnabled
                selectCategoryChip(task.categoryId)
                selectChip(binding.chipGroupPriority, task.priority.toString())
                selectChip(binding.chipGroupRepeat, task.repeatInterval.toString())
                updateDateAndTimeViews()
            }
        } else {
            binding.textViewAddTitle.text = getString(R.string.add_task_title)
            binding.buttonSaveTask.text = getString(R.string.save_task)
            binding.chipCategoryGeneral.isChecked = true
            binding.chipPriorityMedium.isChecked = true
            binding.chipRepeatNone.isChecked = true
        }
    }

    private fun selectChip(chipGroup: com.google.android.material.chip.ChipGroup, value: String) {
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip ?: continue
            if (chip.text.toString() == value) {
                chip.isChecked = true
            }
        }
    }

    private fun showDatePickerDialog() {
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDateCalendar.set(Calendar.YEAR, year)
                selectedDateCalendar.set(Calendar.MONTH, month)
                selectedDateCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDateAndTimeViews()
            },
            selectedDateCalendar.get(Calendar.YEAR),
            selectedDateCalendar.get(Calendar.MONTH),
            selectedDateCalendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun showTimePickerDialog() {
        val timePicker = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedDateCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedDateCalendar.set(Calendar.MINUTE, minute)
                updateDateAndTimeViews()
            },
            selectedDateCalendar.get(Calendar.HOUR_OF_DAY),
            selectedDateCalendar.get(Calendar.MINUTE),
            true
        )
        timePicker.show()
    }

    private fun updateDateAndTimeViews() {
        binding.textViewSelectedDate.text = DateUtils.formatFull(selectedDateCalendar.timeInMillis)
        binding.textViewSelectedTime.text = DateUtils.formatTime(selectedDateCalendar.timeInMillis)
    }

    private fun saveTask() {
        val title = binding.editTextTitle.text.toString().trim()
        val description = binding.editTextDescription.text.toString().trim()
        val tags = binding.editTextTags.text.toString().trim()

        if (title.isEmpty()) {
            binding.editTextTitle.error = getString(R.string.task_title_required)
            binding.editTextTitle.requestFocus()
            return
        }

        val reminderEnabled = binding.switchReminder.isChecked
        val dueDate = selectedDateCalendar.timeInMillis

        if (reminderEnabled && dueDate < System.currentTimeMillis()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.past_date_warning)
                .setPositiveButton(R.string.yes) { _, _ -> persistTask(title, description, tags, reminderEnabled, dueDate) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            persistTask(title, description, tags, reminderEnabled, dueDate)
        }
    }

    private fun persistTask(
        title: String,
        description: String,
        tags: String,
        reminderEnabled: Boolean,
        dueDate: Long
    ) {
        if (isEditing) {
            taskViewModel.allTasks.value?.firstOrNull { it.id == taskId }?.let { existing ->
                val updated = existing.copy(
                    title = title,
                    description = description,
                    dueDate = dueDate,
                    categoryId = selectedCategory,
                    priority = selectedPriority,
                    reminderEnabled = reminderEnabled,
                    repeatInterval = selectedRepeat,
                    tags = tags
                )
                taskViewModel.update(updated)
                if (reminderEnabled) {
                    alarmHelper.setAlarm(
                        taskId, title, description, dueDate, selectedRepeat
                    )
                } else {
                    alarmHelper.cancelAlarm(taskId)
                }
                Toast.makeText(this, R.string.task_updated, Toast.LENGTH_SHORT).show()
            }
        } else {
            taskViewModel.insert(
                Task(
                    title = title,
                    description = description,
                    dueDate = dueDate,
                    categoryId = selectedCategory,
                    priority = selectedPriority,
                    reminderEnabled = reminderEnabled,
                    repeatInterval = selectedRepeat,
                    tags = tags
                )
            ) { id ->
                if (reminderEnabled) {
                    alarmHelper.setAlarm(
                        id.toInt(), title, description, dueDate, selectedRepeat
                    )
                }
            }
            Toast.makeText(this, R.string.task_saved, Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}