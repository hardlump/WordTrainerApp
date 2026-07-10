package com.example.wordtrainer.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.wordtrainer.R
import com.example.wordtrainer.data.local.AppDatabase
import com.example.wordtrainer.domain.Achievement
import com.example.wordtrainer.domain.Leitner
import com.example.wordtrainer.domain.StreakCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Locale

/**
 * Разблокирует достижения по событиям/порогам и управляет заморозками стрика.
 * Разблокировка сопровождается тостом (с любого потока).
 */
class AchievementManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val store: AchievementStore
) {
    private val db = AppDatabase.get(context)
    private val wordDao = db.wordDao()
    private val statsDao = db.statsDao()
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---- События -----------------------------------------------------------

    suspend fun onAnswer(correct: Boolean) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 5 -> tryUnlock(Achievement.NIGHT_OWL)
            hour < 7 -> tryUnlock(Achievement.EARLY_BIRD)
        }
        refresh()
    }

    fun onLanguageAdded() = tryUnlock(Achievement.POLYGLOT)

    fun onDeckImported() = tryUnlock(Achievement.IMPORTER)

    fun onDictionaryAdd() {
        if (store.incDictionaryAddCount() >= 50) tryUnlock(Achievement.DICTIONARY_50)
    }

    // ---- Периодическое обслуживание ---------------------------------------

    /** Раз в день: тратим заморозки на пропущенные дни, затем пересчитываем ачивки. */
    suspend fun dailyMaintenance() = withContext(Dispatchers.IO) {
        consumeFreezesForGap()
        refresh()
    }

    /** Пересчёт пороговых достижений и начисление заморозок. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val days = statsDao.getAllDays()
        val active = days.filter { it.reviewed > 0 }.map { it.date }.toHashSet()
        val streak = StreakCalculator.compute(active, store.frozenDates.value)
        val totalReviews = days.sumOf { it.reviewed }
        val learned = wordDao.countLearnedAll(Leitner.LEARNED_BOX)
        val goal = settings.dailyGoal.value

        if (streak >= 3) tryUnlock(Achievement.STREAK_3)
        if (streak >= 7) tryUnlock(Achievement.STREAK_7)
        if (streak >= 30) tryUnlock(Achievement.STREAK_30)
        if (streak >= 100) tryUnlock(Achievement.STREAK_100)
        if (totalReviews >= 100) tryUnlock(Achievement.REVIEWS_100)
        if (totalReviews >= 1000) tryUnlock(Achievement.REVIEWS_1000)
        if (totalReviews >= 5000) tryUnlock(Achievement.REVIEWS_5000)
        if (learned >= 10) tryUnlock(Achievement.LEARNED_10)
        if (learned >= 100) tryUnlock(Achievement.LEARNED_100)
        if (learned >= 500) tryUnlock(Achievement.LEARNED_500)
        if (days.any { it.reviewed >= 10 && it.correct == it.reviewed }) tryUnlock(Achievement.PERFECT_DAY)
        if (goal > 0 && days.any { it.reviewed >= goal * 2 }) tryUnlock(Achievement.OVERACHIEVER)

        earnFreezes(streak)
    }

    /** +1 заморозка за каждые 7 дней стрика, максимум [MAX_FREEZE] в запасе. */
    private fun earnFreezes(streak: Int) {
        val milestone = streak / 7
        var last = store.lastFreezeMilestone
        if (milestone <= last) return
        var freezes = store.freezes.value
        while (last < milestone) {
            last++
            if (freezes < MAX_FREEZE) freezes++
        }
        store.lastFreezeMilestone = last
        store.setFreezes(freezes)
    }

    /**
     * Закрывает пропущенные дни заморозками, если это спасает существующий стрик.
     * Идёт от вчера назад: собирает подряд пропущенные дни (не больше, чем есть
     * заморозок), и морозит их только если за ними есть «покрытый» день.
     */
    private suspend fun consumeFreezesForGap() {
        val freezes = store.freezes.value
        if (freezes <= 0) return

        val days = statsDao.getAllDays()
        val active = days.filter { it.reviewed > 0 }.map { it.date }.toHashSet()
        val frozen = store.frozenDates.value.toHashSet()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        val gap = ArrayList<String>()
        var reachedCovered = false
        for (i in 0 until freezes) {
            val d = fmt.format(cal.time)
            if (d in active || d in frozen) { reachedCovered = true; break }
            gap.add(d)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        if (!reachedCovered) {
            val d = fmt.format(cal.time)
            reachedCovered = d in active || d in frozen
        }
        if (reachedCovered && gap.isNotEmpty()) {
            frozen.addAll(gap)
            store.setFrozenDates(frozen)
            store.setFreezes(freezes - gap.size)
        }
    }

    private fun tryUnlock(achievement: Achievement) {
        if (store.unlock(achievement.name)) {
            mainHandler.post {
                Toast.makeText(
                    context.applicationContext,
                    context.getString(R.string.achievement_unlocked, context.getString(achievement.titleRes)),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private companion object {
        const val MAX_FREEZE = 2
    }
}
