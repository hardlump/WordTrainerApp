package com.example.wordtrainer.ui.achievements

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wordtrainer.R
import com.example.wordtrainer.WordTrainerApp
import com.example.wordtrainer.databinding.ActivityAchievementsBinding
import com.example.wordtrainer.domain.Achievement
import kotlinx.coroutines.launch

class AchievementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAchievementsBinding
    private val store by lazy { (application as WordTrainerApp).achievementStore }
    private val adapter = AchievementAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        lifecycleScope.launch {
            this@AchievementsActivity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                store.unlocked.collect { unlocked ->
                    val rows = Achievement.entries.map { AchievementRow(it, unlocked.containsKey(it.name)) }
                    binding.countText.text = getString(
                        R.string.achievements_count, unlocked.size, Achievement.entries.size
                    )
                    // Открытые — сверху.
                    adapter.submitList(rows.sortedByDescending { it.unlocked })
                }
            }
        }
    }
}
