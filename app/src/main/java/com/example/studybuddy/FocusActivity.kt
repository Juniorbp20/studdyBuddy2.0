package com.example.studybuddy

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studybuddy.databinding.ActivityFocusBinding
import com.example.studybuddy.util.FocusSessionStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class FocusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusBinding

    private var isWorkMode = true
    private var timer: CountDownTimer? = null
    private var remainingMillis = DEFAULT_WORK_MINUTES * 60_000L
    private var isRunning = false
    private var workMinutes = DEFAULT_WORK_MINUTES
    private var breakMinutes = DEFAULT_BREAK_MINUTES

    companion object {
        private const val DEFAULT_WORK_MINUTES = 25
        private const val DEFAULT_BREAK_MINUTES = 5
        private const val MAX_MINUTES = 600
        private const val PREFS_NAME = "focus_prefs"
        private const val KEY_WORK_MINUTES = "work_minutes"
        private const val KEY_BREAK_MINUTES = "break_minutes"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        workMinutes = prefs.getInt(KEY_WORK_MINUTES, DEFAULT_WORK_MINUTES)
        breakMinutes = prefs.getInt(KEY_BREAK_MINUTES, DEFAULT_BREAK_MINUTES)
        remainingMillis = currentDurationMillis()

        updateSessionsLabel()
        updateTimerDisplay()

        binding.buttonToggle.setOnClickListener {
            if (isRunning) pauseTimer() else startTimer()
        }
        binding.buttonReset.setOnClickListener { resetTimer() }
        binding.textTimer.setOnClickListener { showDurationDialog() }
        binding.textTimeHint.setOnClickListener { showDurationDialog() }
    }

    private fun startTimer() {
        isRunning = true
        binding.buttonToggle.text = getString(R.string.focus_pause)
        timer = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                updateTimerDisplay()
            }

            override fun onFinish() {
                onTimerFinished()
            }
        }.start()
    }

    private fun pauseTimer() {
        isRunning = false
        binding.buttonToggle.text = getString(R.string.focus_resume)
        timer?.cancel()
    }

    private fun resetTimer() {
        pauseTimer()
        remainingMillis = currentDurationMillis()
        updateTimerDisplay()
    }

    private fun onTimerFinished() {
        vibrate()
        if (isWorkMode) {
            FocusSessionStore.recordSession(this)
            updateSessionsLabel()
            Toast.makeText(this, R.string.focus_completed_toast, Toast.LENGTH_LONG).show()
            isWorkMode = false
            binding.textMode.text = getString(R.string.break_mode)
        } else {
            Toast.makeText(this, R.string.focus_break_toast, Toast.LENGTH_LONG).show()
            isWorkMode = true
            binding.textMode.text = getString(R.string.focus_mode)
        }
        remainingMillis = currentDurationMillis()
        isRunning = false
        binding.buttonToggle.text = getString(R.string.focus_start)
        updateTimerDisplay()
    }

    private fun currentDurationMillis(): Long {
        return (if (isWorkMode) workMinutes else breakMinutes) * 60_000L
    }

    private fun showDurationDialog() {
        if (isRunning) {
            Toast.makeText(this, R.string.focus_pause_to_change, Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((if (isWorkMode) workMinutes else breakMinutes).toString())
            selectAll()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (isWorkMode) R.string.focus_work_duration_title else R.string.focus_break_duration_title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val minutes = input.text.toString().toIntOrNull()?.coerceIn(1, MAX_MINUTES) ?: 1
                if (isWorkMode) workMinutes = minutes else breakMinutes = minutes
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putInt(KEY_WORK_MINUTES, workMinutes)
                    .putInt(KEY_BREAK_MINUTES, breakMinutes)
                    .apply()
                remainingMillis = currentDurationMillis()
                updateTimerDisplay()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Vibrator::class.java)
            @Suppress("DEPRECATION")
            vibrator.vibrate(800)
        }
    }

    private fun updateTimerDisplay() {
        val totalSeconds = remainingMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        binding.textTimer.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        binding.progressTimer.max = 100
        val total = currentDurationMillis()
        binding.progressTimer.progress = ((total - remainingMillis) * 100 / total).toInt()
    }

    private fun updateSessionsLabel() {
        binding.textSessions.text = getString(
            R.string.focus_sessions_label,
            FocusSessionStore.sessionsToday(this),
            FocusSessionStore.totalSessions(this)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}