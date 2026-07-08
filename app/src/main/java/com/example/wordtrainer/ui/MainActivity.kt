package com.example.wordtrainer.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.wordtrainer.R
import com.example.wordtrainer.WordTrainerApp
import com.example.wordtrainer.databinding.ActivityMainBinding
import com.example.wordtrainer.domain.Direction
import com.example.wordtrainer.domain.Language
import com.example.wordtrainer.domain.TtsMode
import com.example.wordtrainer.ui.flashcards.FlashcardsFragment
import com.example.wordtrainer.ui.quiz.QuizFragment
import com.example.wordtrainer.ui.stats.StatsFragment
import com.example.wordtrainer.ui.wordlist.WordListFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val settings by lazy { (application as WordTrainerApp).settings }
    private val repository by lazy { (application as WordTrainerApp).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Прогреваем колоду текущего языка в фоне при старте.
        lifecycleScope.launch { repository.seedIfNeeded(settings.language.value, settings) }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_cards -> FlashcardsFragment()
                R.id.nav_quiz -> QuizFragment()
                R.id.nav_words -> WordListFragment()
                R.id.nav_stats -> StatsFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) binding.bottomNav.selectedItemId = R.id.nav_cards
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_direction -> { toggleDirection(); true }
        R.id.action_language -> { chooseLanguage(); true }
        R.id.action_tts -> { chooseTts(); true }
        R.id.action_goal -> { chooseGoal(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun toggleDirection() {
        val next = settings.direction.value.toggled()
        settings.setDirection(next)
        val text = if (next == Direction.WORD_TO_TRANSLATION)
            getString(R.string.direction_forward) else getString(R.string.direction_backward)
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun chooseLanguage() {
        val languages = Language.entries
        val titles = languages.map { it.title }.toTypedArray()
        val current = languages.indexOf(settings.language.value)
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_language)
            .setSingleChoiceItems(titles, current) { dialog, which ->
                val lang = languages[which]
                settings.setLanguage(lang)
                lifecycleScope.launch { repository.seedIfNeeded(lang, settings) }
                dialog.dismiss()
            }
            .show()
    }

    private fun chooseTts() {
        val modes = arrayOf(getString(R.string.tts_offline), getString(R.string.tts_online))
        val current = if (settings.ttsMode.value == TtsMode.OFFLINE) 0 else 1
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_tts)
            .setSingleChoiceItems(modes, current) { dialog, which ->
                settings.setTtsMode(if (which == 0) TtsMode.OFFLINE else TtsMode.ONLINE)
                dialog.dismiss()
            }
            .show()
    }

    private fun chooseGoal() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(settings.dailyGoal.value.toString())
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.daily_goal_title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                input.text.toString().toIntOrNull()?.let { settings.setDailyGoal(it) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
