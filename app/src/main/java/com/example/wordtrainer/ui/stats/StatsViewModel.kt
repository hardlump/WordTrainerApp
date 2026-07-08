package com.example.wordtrainer.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wordtrainer.data.SettingsStore
import com.example.wordtrainer.data.WordRepository
import com.example.wordtrainer.domain.DayActivity
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
    val reviewedToday: Int = 0,
    val correctToday: Int = 0,
    val wrongToday: Int = 0,
    val dailyGoal: Int = 20,
    val activity: List<DayActivity> = emptyList()
) {
    val accuracyToday: Int
        get() = if (reviewedToday == 0) 0 else correctToday * 100 / reviewedToday
    val goalProgress: Int
        get() = if (dailyGoal == 0) 100 else (reviewedToday * 100 / dailyGoal).coerceAtMost(100)
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val repo: WordRepository,
    private val settings: SettingsStore
) : ViewModel() {

    val state: StateFlow<StatsState> =
        settings.language.flatMapLatest { lang ->
            combine(
                repo.observeTotal(lang),
                repo.observeLearned(lang),
                repo.observeDueCount(lang),
                combine(repo.observeStreak(), repo.observeActivity()) { streak, activity -> streak to activity },
                combine(repo.observeToday(), settings.dailyGoal) { today, goal -> today to goal }
            ) { total, learned, due, streakActivity, todayGoal ->
                val (streak, activity) = streakActivity
                val (today, goal) = todayGoal
                StatsState(
                    total = total,
                    learned = learned,
                    due = due,
                    streak = streak,
                    reviewedToday = today.reviewed,
                    correctToday = today.correct,
                    wrongToday = today.wrong,
                    dailyGoal = goal,
                    activity = activity
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsState())
}
