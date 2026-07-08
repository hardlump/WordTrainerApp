package com.example.wordtrainer.ui.settings

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.wordtrainer.R
import com.example.wordtrainer.WordTrainerApp
import com.example.wordtrainer.databinding.ActivitySettingsBinding
import com.example.wordtrainer.domain.Direction
import com.example.wordtrainer.domain.Language
import com.example.wordtrainer.domain.TtsMode
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings by lazy { (application as WordTrainerApp).settings }
    private val repository by lazy { (application as WordTrainerApp).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rowLanguage.setOnClickListener { chooseLanguage() }
        binding.rowDirection.setOnClickListener { chooseDirection() }
        binding.rowTts.setOnClickListener { chooseTts() }
        binding.rowGoal.setOnClickListener { chooseGoal() }

        lifecycleScope.launch {
            this@SettingsActivity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { settings.language.collect { binding.languageValue.text = it.title } }
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
            }
        }
    }

    private fun chooseLanguage() {
        val languages = Language.entries
        val titles = languages.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.set_language)
            .setSingleChoiceItems(titles, languages.indexOf(settings.language.value)) { dialog, which ->
                val lang = languages[which]
                settings.setLanguage(lang)
                lifecycleScope.launch { repository.seedIfNeeded(lang, settings) }
                dialog.dismiss()
            }
            .show()
    }

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
