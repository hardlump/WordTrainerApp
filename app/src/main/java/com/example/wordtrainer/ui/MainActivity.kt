package com.example.wordtrainer.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.wordtrainer.R
import com.example.wordtrainer.WordTrainerApp
import com.example.wordtrainer.databinding.ActivityMainBinding
import com.example.wordtrainer.domain.Direction
import com.example.wordtrainer.ui.dictionary.DictionaryActivity
import com.example.wordtrainer.ui.flashcards.FlashcardsFragment
import com.example.wordtrainer.ui.quiz.QuizFragment
import com.example.wordtrainer.ui.settings.SettingsActivity
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

        // Создаём языки по умолчанию и прогреваем колоду текущего языка в фоне.
        lifecycleScope.launch {
            repository.ensureDefaultLanguages()
            repository.seedIfNeeded(settings.language.value, settings)
        }

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
        R.id.action_dictionary -> { startActivity(Intent(this, DictionaryActivity::class.java)); true }
        R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
    }

    /** Быстрое переключение направления прямо из тулбара во время тренировки. */
    private fun toggleDirection() {
        val next = settings.direction.value.toggled()
        settings.setDirection(next)
        val text = if (next == Direction.WORD_TO_TRANSLATION)
            getString(R.string.direction_forward) else getString(R.string.direction_backward)
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
