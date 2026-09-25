package com.example.studybuddy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.studybuddy.databinding.ActivityFocusBinding
import com.example.studybuddy.databinding.DialogPomodoroConfigBinding
import com.example.studybuddy.notification.FocusAlarmHelper
import com.example.studybuddy.notification.FocusAlarmReceiver
import com.example.studybuddy.notification.NotificationHelper
import com.example.studybuddy.util.FocusSessionStore
import com.example.studybuddy.util.PomodoroSoundPlayer
import com.example.studybuddy.util.SyncManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Locale

class FocusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusBinding
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var focusAlarmHelper: FocusAlarmHelper

    private var currentMode = FocusAlarmReceiver.MODE_FOCUS
    private var timer: CountDownTimer? = null
    private var remainingMillis = DEFAULT_WORK_MINUTES * 60_000L
    private var isRunning = false
    private var targetEndTimeMillis = 0L

    private var workMinutes = DEFAULT_WORK_MINUTES
    private var shortBreakMinutes = DEFAULT_SHORT_BREAK_MINUTES
    private var longBreakMinutes = DEFAULT_LONG_BREAK_MINUTES
    private var cyclesBeforeLongBreak = DEFAULT_CYCLES_BEFORE_LONG
    private var completedCycles = 0

    companion object {
        const val EXTRA_START_IMMEDIATELY = "extra_start_immediately"
        private const val DEFAULT_WORK_MINUTES = 25
        private const val DEFAULT_SHORT_BREAK_MINUTES = 5
        private const val DEFAULT_LONG_BREAK_MINUTES = 15
        private const val DEFAULT_CYCLES_BEFORE_LONG = 4
        private const val MAX_MINUTES = 180
        private const val MIN_MINUTES = 1

        private const val PREFS_NAME = "focus_prefs"
        private const val KEY_WORK_MINUTES = "work_minutes"
        private const val KEY_SHORT_BREAK_MINUTES = "short_break_minutes"
        private const val KEY_LONG_BREAK_MINUTES = "long_break_minutes"
        private const val KEY_CYCLES_BEFORE_LONG = "cycles_before_long"
        private const val KEY_CURRENT_MODE = "current_mode"
        private const val KEY_COMPLETED_CYCLES = "completed_cycles"
        private const val KEY_REMAINING_MILLIS = "remaining_millis"
        private const val KEY_TARGET_END_MILLIS = "target_end_millis"
        private const val KEY_IS_RUNNING = "is_running"
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    R.string.notification_permission_denied,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        notificationHelper = NotificationHelper(this)
        focusAlarmHelper = FocusAlarmHelper(this)

        loadPreferences()
        setupListeners()
        setupPresets()

        if (intent.getBooleanExtra(EXTRA_START_IMMEDIATELY, false)) {
            binding.root.post {
                if (!isRunning) {
                    startTimer()
                }
            }
        }

        requestNotificationPermissionIfNeeded()
        observeSyncState()
    }

    private fun observeSyncState() {
        val syncManager = SyncManager.getInstance(this)
        lifecycleScope.launch {
            syncManager.syncState.collect { state ->
                if (!state.isOnline) {
                    binding.iconFocusSync.setImageResource(R.drawable.ic_cloud_off)
                    binding.iconFocusSync.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(this@FocusActivity, android.R.color.holo_orange_dark)
                    )
                    binding.textFocusSync.setText(R.string.sync_status_offline)
                    binding.textFocusSync.setTextColor(
                        ContextCompat.getColor(this@FocusActivity, android.R.color.holo_orange_dark)
                    )
                } else if (state.isSyncing) {
                    binding.iconFocusSync.setImageResource(R.drawable.ic_sync)
                    binding.iconFocusSync.imageTintList = ColorStateList.valueOf(
                        MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
                    )
                    binding.textFocusSync.setText(R.string.sync_status_syncing)
                    binding.textFocusSync.setTextColor(
                        MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
                    )
                } else {
                    binding.iconFocusSync.setImageResource(R.drawable.ic_cloud_done)
                    binding.iconFocusSync.imageTintList = ColorStateList.valueOf(
                        MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
                    )
                    binding.textFocusSync.setText(R.string.sync_status_online)
                    binding.textFocusSync.setTextColor(
                        MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationHelper.cancelFocusOngoingNotification()
        notificationHelper.cancelFocusCompletedNotification()
        syncTimerWithBackgroundState()
        updateSessionsLabel()
        updateModeDisplay()
        updateTimerDisplay()
        updatePresetChipSelection()
    }

    override fun onStop() {
        super.onStop()
        if (isRunning && remainingMillis > 0) {
            val sessionProgress = getCycleSummaryText()
            val timeText = formatTime(remainingMillis)
            val isFocus = currentMode == FocusAlarmReceiver.MODE_FOCUS
            notificationHelper.showFocusOngoingNotification(timeText, isFocus, sessionProgress)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        workMinutes = prefs.getInt(KEY_WORK_MINUTES, DEFAULT_WORK_MINUTES)
        shortBreakMinutes = prefs.getInt(KEY_SHORT_BREAK_MINUTES, DEFAULT_SHORT_BREAK_MINUTES)
        longBreakMinutes = prefs.getInt(KEY_LONG_BREAK_MINUTES, DEFAULT_LONG_BREAK_MINUTES)
        cyclesBeforeLongBreak = prefs.getInt(KEY_CYCLES_BEFORE_LONG, DEFAULT_CYCLES_BEFORE_LONG)
        currentMode = prefs.getInt(KEY_CURRENT_MODE, FocusAlarmReceiver.MODE_FOCUS)
        completedCycles = prefs.getInt(KEY_COMPLETED_CYCLES, 0)
        isRunning = prefs.getBoolean(KEY_IS_RUNNING, false)
        targetEndTimeMillis = prefs.getLong(KEY_TARGET_END_MILLIS, 0L)
        remainingMillis = prefs.getLong(KEY_REMAINING_MILLIS, currentDurationMillis())

        if (remainingMillis <= 0) {
            remainingMillis = currentDurationMillis()
        }
    }

    private fun savePreferences() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_WORK_MINUTES, workMinutes)
            .putInt(KEY_SHORT_BREAK_MINUTES, shortBreakMinutes)
            .putInt(KEY_LONG_BREAK_MINUTES, longBreakMinutes)
            .putInt(KEY_CYCLES_BEFORE_LONG, cyclesBeforeLongBreak)
            .putInt(KEY_CURRENT_MODE, currentMode)
            .putInt(KEY_COMPLETED_CYCLES, completedCycles)
            .putBoolean(KEY_IS_RUNNING, isRunning)
            .putLong(KEY_TARGET_END_MILLIS, targetEndTimeMillis)
            .putLong(KEY_REMAINING_MILLIS, remainingMillis)
            .apply()

        // Persist snapshot to SyncManager for local offline resilience
        SyncManager.getInstance(this).enqueuePomodoroTimerState(this, currentMode, remainingMillis, isRunning)
    }

    private fun syncTimerWithBackgroundState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentMode = prefs.getInt(KEY_CURRENT_MODE, currentMode)
        completedCycles = prefs.getInt(KEY_COMPLETED_CYCLES, completedCycles)
        isRunning = prefs.getBoolean(KEY_IS_RUNNING, isRunning)
        targetEndTimeMillis = prefs.getLong(KEY_TARGET_END_MILLIS, targetEndTimeMillis)

        if (isRunning && targetEndTimeMillis > 0) {
            val now = System.currentTimeMillis()
            val diff = targetEndTimeMillis - now
            if (diff <= 0) {
                // Timer finished while app was in background
                timer?.cancel()
                onTimerFinished(fromBackground = true)
            } else {
                remainingMillis = diff
                startInternalCountDown(diff)
            }
        } else {
            timer?.cancel()
            binding.buttonToggle.text = if (remainingMillis < currentDurationMillis() && remainingMillis > 0) {
                getString(R.string.focus_resume)
            } else {
                getString(R.string.focus_start)
            }
        }
    }

    private fun setupListeners() {
        binding.buttonToggle.setOnClickListener {
            if (isRunning) pauseTimer() else startTimer()
        }
        binding.buttonReset.setOnClickListener { resetTimer() }
        binding.buttonSkip.setOnClickListener { skipInterval() }
        binding.textTimer.setOnClickListener { showIntervalsConfigDialog() }
        binding.textTimeHint.setOnClickListener { showIntervalsConfigDialog() }
    }

    private fun setupPresets() {
        binding.chipClassic.setOnClickListener { applyPreset(25, 5, 15, 4) }
        binding.chipDeep.setOnClickListener { applyPreset(50, 10, 20, 4) }
        binding.chipSprint.setOnClickListener { applyPreset(15, 3, 10, 4) }
        binding.chipCustom.setOnClickListener { showIntervalsConfigDialog() }
    }

    private fun applyPreset(work: Int, shortB: Int, longB: Int, cycles: Int) {
        if (isRunning) {
            Toast.makeText(this, R.string.focus_pause_to_change, Toast.LENGTH_SHORT).show()
            updatePresetChipSelection()
            return
        }
        workMinutes = work
        shortBreakMinutes = shortB
        longBreakMinutes = longB
        cyclesBeforeLongBreak = cycles
        remainingMillis = currentDurationMillis()
        savePreferences()
        updateModeDisplay()
        updateTimerDisplay()
        updatePresetChipSelection()
    }

    private fun updatePresetChipSelection() {
        binding.chipGroupPresets.clearCheck()
        when {
            workMinutes == 25 && shortBreakMinutes == 5 && longBreakMinutes == 15 && cyclesBeforeLongBreak == 4 ->
                binding.chipClassic.isChecked = true
            workMinutes == 50 && shortBreakMinutes == 10 && longBreakMinutes == 20 && cyclesBeforeLongBreak == 4 ->
                binding.chipDeep.isChecked = true
            workMinutes == 15 && shortBreakMinutes == 3 && longBreakMinutes == 10 && cyclesBeforeLongBreak == 4 ->
                binding.chipSprint.isChecked = true
            else ->
                binding.chipCustom.isChecked = true
        }
    }

    private fun startTimer() {
        requestNotificationPermissionIfNeeded()

        isRunning = true
        targetEndTimeMillis = System.currentTimeMillis() + remainingMillis
        savePreferences()

        val isLongBreak = (completedCycles + 1) >= cyclesBeforeLongBreak
        val nextBreakMinutes = if (isLongBreak) longBreakMinutes else shortBreakMinutes

        focusAlarmHelper.scheduleFocusAlarm(
            targetEndTimeMillis,
            currentMode,
            nextBreakMinutes,
            isLongBreak
        )

        binding.buttonToggle.text = getString(R.string.focus_pause)
        startInternalCountDown(remainingMillis)
    }

    private fun startInternalCountDown(durationMillis: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                updateTimerDisplay()
            }

            override fun onFinish() {
                onTimerFinished(fromBackground = false)
            }
        }.start()
    }

    private fun pauseTimer() {
        isRunning = false
        targetEndTimeMillis = 0L
        focusAlarmHelper.cancelFocusAlarm()
        notificationHelper.cancelFocusOngoingNotification()
        timer?.cancel()
        binding.buttonToggle.text = getString(R.string.focus_resume)
        savePreferences()
    }

    private fun resetTimer() {
        pauseTimer()
        remainingMillis = currentDurationMillis()
        binding.buttonToggle.text = getString(R.string.focus_start)
        savePreferences()
        updateTimerDisplay()
    }

    private fun skipInterval() {
        if (isRunning) {
            pauseTimer()
        }
        transitionToNextInterval(userSkipped = true)
    }

    private fun onTimerFinished(fromBackground: Boolean) {
        focusAlarmHelper.cancelFocusAlarm()
        notificationHelper.cancelFocusOngoingNotification()
        timer?.cancel()

        if (!fromBackground) {
            vibrate()
            val isFocus = currentMode == FocusAlarmReceiver.MODE_FOCUS
            PomodoroSoundPlayer.playSessionAlert(this, isFocusEnd = isFocus)
        }

        transitionToNextInterval(userSkipped = false, notifyUser = !fromBackground)
    }

    private fun transitionToNextInterval(userSkipped: Boolean = false, notifyUser: Boolean = true) {
        if (currentMode == FocusAlarmReceiver.MODE_FOCUS) {
            if (!userSkipped) {
                FocusSessionStore.recordSession(this)
                if (com.example.studybuddy.util.StudyGoalManager.isGoalMetToday(this)) {
                    notificationHelper.cancelDailyGoalReminderNotification()
                }
                updateSessionsLabel()
            }

            completedCycles++
            val isLong = completedCycles >= cyclesBeforeLongBreak
            val nextBreakMins = if (isLong) longBreakMinutes else shortBreakMinutes

            if (notifyUser) {
                Toast.makeText(this, R.string.focus_completed_toast, Toast.LENGTH_LONG).show()
                notificationHelper.showFocusCompletedNotification(nextBreakMins, isLong)
            }

            if (isLong) {
                currentMode = FocusAlarmReceiver.MODE_LONG_BREAK
                completedCycles = 0
            } else {
                currentMode = FocusAlarmReceiver.MODE_SHORT_BREAK
            }
        } else {
            // Was short break or long break -> switch to focus
            if (notifyUser) {
                Toast.makeText(this, R.string.focus_break_toast, Toast.LENGTH_LONG).show()
                notificationHelper.showBreakCompletedNotification()
            }
            currentMode = FocusAlarmReceiver.MODE_FOCUS
        }

        isRunning = false
        targetEndTimeMillis = 0L
        remainingMillis = currentDurationMillis()
        binding.buttonToggle.text = getString(R.string.focus_start)
        savePreferences()

        updateModeDisplay()
        updateTimerDisplay()
    }

    private fun currentDurationMillis(): Long {
        val minutes = when (currentMode) {
            FocusAlarmReceiver.MODE_FOCUS -> workMinutes
            FocusAlarmReceiver.MODE_SHORT_BREAK -> shortBreakMinutes
            FocusAlarmReceiver.MODE_LONG_BREAK -> longBreakMinutes
            else -> workMinutes
        }
        return minutes * 60_000L
    }

    private fun updateModeDisplay() {
        val colorPrimary = com.google.android.material.color.MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorPrimary
        )
        val colorTertiary = com.google.android.material.color.MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorTertiary
        )

        when (currentMode) {
            FocusAlarmReceiver.MODE_FOCUS -> {
                binding.textMode.text = getString(R.string.focus_mode)
                binding.textMode.setTextColor(colorPrimary)
                binding.progressTimer.setIndicatorColor(colorPrimary)
                val sessionNum = (completedCycles % cyclesBeforeLongBreak) + 1
                binding.textCycle.text = getString(
                    R.string.focus_cycle_indicator,
                    sessionNum,
                    cyclesBeforeLongBreak
                )
            }
            FocusAlarmReceiver.MODE_SHORT_BREAK -> {
                binding.textMode.text = getString(R.string.break_short_mode)
                binding.textMode.setTextColor(colorTertiary)
                binding.progressTimer.setIndicatorColor(colorTertiary)
                binding.textCycle.text = getString(R.string.focus_short_break_label)
            }
            FocusAlarmReceiver.MODE_LONG_BREAK -> {
                binding.textMode.text = getString(R.string.break_long_mode)
                binding.textMode.setTextColor(colorTertiary)
                binding.progressTimer.setIndicatorColor(colorTertiary)
                binding.textCycle.text = getString(R.string.focus_long_break_label)
            }
        }
    }

    private fun updateTimerDisplay() {
        val totalSeconds = remainingMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        binding.textTimer.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

        val total = currentDurationMillis().coerceAtLeast(1000L)
        val progress = (((total - remainingMillis).coerceAtLeast(0) * 1000) / total).toInt()
        binding.progressTimer.max = 1000
        binding.progressTimer.progress = progress.coerceIn(0, 1000)
    }

    private fun updateSessionsLabel() {
        binding.textSessions.text = getString(
            R.string.focus_sessions_label,
            FocusSessionStore.sessionsToday(this),
            FocusSessionStore.totalSessions(this)
        )
    }

    private fun getCycleSummaryText(): String {
        return when (currentMode) {
            FocusAlarmReceiver.MODE_FOCUS ->
                getString(R.string.focus_cycle_indicator, (completedCycles % cyclesBeforeLongBreak) + 1, cyclesBeforeLongBreak)
            FocusAlarmReceiver.MODE_SHORT_BREAK ->
                getString(R.string.break_short_mode)
            FocusAlarmReceiver.MODE_LONG_BREAK ->
                getString(R.string.break_long_mode)
            else -> ""
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun showIntervalsConfigDialog() {
        if (isRunning) {
            Toast.makeText(this, R.string.focus_pause_to_change, Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogPomodoroConfigBinding.inflate(layoutInflater)
        dialogBinding.editWorkDuration.setText(workMinutes.toString())
        dialogBinding.editShortBreakDuration.setText(shortBreakMinutes.toString())
        dialogBinding.editLongBreakDuration.setText(longBreakMinutes.toString())
        dialogBinding.editCycles.setText(cyclesBeforeLongBreak.toString())
        dialogBinding.switchSoundAlert.isChecked = PomodoroSoundPlayer.isSoundEnabled(this)

        dialogBinding.buttonPreviewSound.setOnClickListener {
            val isFocus = currentMode == FocusAlarmReceiver.MODE_FOCUS
            PomodoroSoundPlayer.playSessionAlert(this, isFocusEnd = isFocus, force = true)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.focus_config_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val newWork = dialogBinding.editWorkDuration.text.toString().toIntOrNull()
                    ?.coerceIn(MIN_MINUTES, MAX_MINUTES) ?: workMinutes
                val newShort = dialogBinding.editShortBreakDuration.text.toString().toIntOrNull()
                    ?.coerceIn(MIN_MINUTES, MAX_MINUTES) ?: shortBreakMinutes
                val newLong = dialogBinding.editLongBreakDuration.text.toString().toIntOrNull()
                    ?.coerceIn(MIN_MINUTES, MAX_MINUTES) ?: longBreakMinutes
                val newCycles = dialogBinding.editCycles.text.toString().toIntOrNull()
                    ?.coerceIn(1, 12) ?: cyclesBeforeLongBreak

                workMinutes = newWork
                shortBreakMinutes = newShort
                longBreakMinutes = newLong
                cyclesBeforeLongBreak = newCycles
                remainingMillis = currentDurationMillis()

                PomodoroSoundPlayer.setSoundEnabled(this, dialogBinding.switchSoundAlert.isChecked)
                invalidateOptionsMenu()

                savePreferences()
                updateModeDisplay()
                updateTimerDisplay()
                updatePresetChipSelection()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 400, 200, 400),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Vibrator::class.java)
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 400, 200, 400), -1)
            }
        } catch (_: Exception) {
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_focus, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val soundItem = menu.findItem(R.id.action_sound_toggle)
        if (soundItem != null) {
            val soundEnabled = PomodoroSoundPlayer.isSoundEnabled(this)
            soundItem.setIcon(if (soundEnabled) R.drawable.ic_volume_up else R.drawable.ic_volume_off)
            soundItem.title = getString(if (soundEnabled) R.string.focus_sound_enabled else R.string.focus_sound_disabled)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sound_toggle -> {
                val newEnabled = !PomodoroSoundPlayer.isSoundEnabled(this)
                PomodoroSoundPlayer.setSoundEnabled(this, newEnabled)
                invalidateOptionsMenu()
                Toast.makeText(
                    this,
                    if (newEnabled) R.string.focus_sound_enabled else R.string.focus_sound_disabled,
                    Toast.LENGTH_SHORT
                ).show()
                if (newEnabled) {
                    val isFocus = currentMode == FocusAlarmReceiver.MODE_FOCUS
                    PomodoroSoundPlayer.playSessionAlert(this, isFocusEnd = isFocus, force = true)
                }
                true
            }
            R.id.action_focus_settings -> {
                showIntervalsConfigDialog()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
