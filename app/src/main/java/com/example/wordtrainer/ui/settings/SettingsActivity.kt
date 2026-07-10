package com.example.wordtrainer.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.wordtrainer.R
import com.example.wordtrainer.WordTrainerApp
import com.example.wordtrainer.databinding.ActivitySettingsBinding
import com.example.wordtrainer.domain.Direction
import com.example.wordtrainer.domain.TtsMode
import com.example.wordtrainer.reminders.ReminderScheduler
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings by lazy { (application as WordTrainerApp).settings }
    private val repository by lazy { (application as WordTrainerApp).repository }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                settings.setRemindersEnabled(true)
                ReminderScheduler.schedule(this, settings.reminderHour.value, settings.reminderMinute.value)
            } else {
                Toast.makeText(this, R.string.reminder_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rowLanguage.setOnClickListener {
            startActivity(Intent(this, LanguagesActivity::class.java))
        }
        binding.rowDirection.setOnClickListener { chooseDirection() }
        binding.rowTts.setOnClickListener { chooseTts() }
        binding.rowGoal.setOnClickListener { chooseGoal() }
        binding.rowReminders.setOnClickListener { toggleReminders() }
        binding.rowReminderTime.setOnClickListener { pickReminderTime() }

        lifecycleScope.launch {
            this@SettingsActivity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(settings.language, repository.observeLanguages()) { code, langs ->
                        langs.firstOrNull { it.code == code }?.name ?: code
                    }.collect { binding.languageValue.text = it }
                }
                launch {
                    settings.direction.collect {
                        binding.directionValue.text = getString(
                            if (it == Direction.WORD_TO_TRANSLATION) R.string.direction_forward
                            else R.string.direction_backward
                        )
                    }
                }
                launch {
                    settings.ttsMode.collect {
                        binding.ttsValue.text = getString(
                            if (it == TtsMode.OFFLINE) R.string.tts_offline else R.string.tts_online
                        )
                    }
                }
                launch { settings.dailyGoal.collect { binding.goalValue.text = it.toString() } }
                launch {
                    settings.remindersEnabled.collect {
                        binding.remindersValue.text =
                            getString(if (it) R.string.reminder_on else R.string.reminder_off)
                    }
                }
                launch {
                    combine(settings.reminderHour, settings.reminderMinute) { h, m -> formatTime(h, m) }
                        .collect { binding.reminderTimeValue.text = it }
                }
            }
        }
    }

    private fun toggleReminders() {
        if (settings.remindersEnabled.value) {
            settings.setRemindersEnabled(false)
            ReminderScheduler.cancel(this)
        } else {
            enableReminders()
        }
    }

    private fun enableReminders() {
        // На Android 13+ нужно разрешение POST_NOTIFICATIONS.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            settings.setRemindersEnabled(true)
            ReminderScheduler.schedule(this, settings.reminderHour.value, settings.reminderMinute.value)
        }
    }

    private fun pickReminderTime() {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                settings.setReminderTime(hour, minute)
                if (settings.remindersEnabled.value) ReminderScheduler.schedule(this, hour, minute)
            },
            settings.reminderHour.value,
            settings.reminderMinute.value,
            true
        ).show()
    }

    private fun formatTime(hour: Int, minute: Int): String =
        String.format(Locale.US, "%02d:%02d", hour, minute)

    private fun chooseDirection() {
        val options = arrayOf(getString(R.string.direction_forward), getString(R.string.direction_backward))
        val current = if (settings.direction.value == Direction.WORD_TO_TRANSLATION) 0 else 1
        AlertDialog.Builder(this)
            .setTitle(R.string.set_direction)
            .setSingleChoiceItems(options, current) { dialog, which ->
                settings.setDirection(if (which == 0) Direction.WORD_TO_TRANSLATION else Direction.TRANSLATION_TO_WORD)
                dialog.dismiss()
            }
            .show()
    }

    private fun chooseTts() {
        val options = arrayOf(getString(R.string.tts_offline), getString(R.string.tts_online))
        val current = if (settings.ttsMode.value == TtsMode.OFFLINE) 0 else 1
        AlertDialog.Builder(this)
            .setTitle(R.string.set_tts)
            .setSingleChoiceItems(options, current) { dialog, which ->
                settings.setTtsMode(if (which == 0) TtsMode.OFFLINE else TtsMode.ONLINE)
                dialog.dismiss()
            }
            .show()
    }

    private fun chooseGoal() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(settings.dailyGoal.value.toString())
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.set_goal)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                input.text.toString().toIntOrNull()?.let { settings.setDailyGoal(it) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
