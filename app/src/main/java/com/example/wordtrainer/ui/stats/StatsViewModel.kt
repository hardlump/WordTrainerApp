package com.example.wordtrainer.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wordtrainer.data.AchievementStore
import com.example.wordtrainer.data.SettingsStore
import com.example.wordtrainer.data.WordRepository
import com.example.wordtrainer.domain.ActivityBuilder
import com.example.wordtrainer.domain.DayActivity
import com.example.wordtrainer.domain.LevelSystem
import com.example.wordtrainer.domain.StreakCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class StatsState(
    val total: Int = 0,
    val learned: Int = 0,
    val due: Int = 0,
    val streak: Int = 0,
    val freezes: Int = 0,
    val reviewedToday: Int = 0,
    val correctToday: Int = 0,
    val wrongToday: Int = 0,
    val dailyGoal: Int = 20,
    val activity: List<DayActivity> = emptyList(),
    val heatmap: List<List<Int?>> = emptyList(),
    val level: Int = 1,
    val xpIntoLevel: Int = 0,
    val xpForNextLevel: Int = 100
) {
    val accuracyToday: Int
        get() = if (reviewedToday == 0) 0 else correctToday * 100 / reviewedToday
    val goalProgress: Int
        get() = if (dailyGoal == 0) 100 else (reviewedToday * 100 / dailyGoal).coerceAtMost(100)
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val repo: WordRepository,
    private val settings: SettingsStore,
    private val store: AchievementStore
) : ViewModel() {

    private data class ActivityBundle(
        val activity: List<DayActivity>,
        val heatmap: List<List<Int?>>,
        val streak: Int,
        val totalReviews: Int
    )

    val state: StateFlow<StatsState> =
        settings.language.flatMapLatest { lang ->
            // Из дневной статистики строим сразу: 14-дневный ряд, тепловую карту, стрик и всего повторений.
            val activityBundle = combine(repo.observeAllDays(), store.frozenDates) { days, frozen ->
                val byDate = days.associate { it.date to it.reviewed }
                val active = days.filter { it.reviewed > 0 }.map { it.date }.toHashSet()
                ActivityBundle(
                    activity = ActivityBuilder.recentDays(byDate, ACTIVITY_DAYS),
                    heatmap = ActivityBuilder.heatmap(byDate, HEATMAP_WEEKS),
                    streak = StreakCalculator.compute(active, frozen),
                    totalReviews = days.sumOf { it.reviewed }
                )
            }
            val todayGoalFreeze = combine(
                repo.observeToday(), settings.dailyGoal, store.freezes
            ) { today, goal, freezes -> Triple(today, goal, freezes) }

            combine(
                repo.observeTotal(lang),
                repo.observeLearned(lang),
                repo.observeDueCount(lang),
                activityBundle,
                todayGoalFreeze
            ) { total, learned, due, bundle, tgf ->
                val (today, goal, freezes) = tgf
                val level = LevelSystem.levelOf(LevelSystem.xp(bundle.totalReviews, learned))
                StatsState(
                    total = total,
                    learned = learned,
                    due = due,
                    streak = bundle.streak,
                    freezes = freezes,
                    reviewedToday = today.reviewed,
                    correctToday = today.correct,
                    wrongToday = today.wrong,
                    dailyGoal = goal,
                    activity = bundle.activity,
                    heatmap = bundle.heatmap,
                    level = level.level,
                    xpIntoLevel = level.xpIntoLevel,
                    xpForNextLevel = level.xpForNextLevel
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsState())

    private companion object {
        const val ACTIVITY_DAYS = 14
        const val HEATMAP_WEEKS = 17
    }
}
