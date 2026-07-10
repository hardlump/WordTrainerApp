package com.example.wordtrainer.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Расчёт стрика с учётом «замороженных» дней (заморозка стрика). */
object StreakCalculator {

    /**
     * Считает подряд идущие дни от сегодня назад, где была активность
     * ([activeDates]) или день заморожен ([frozen]). Если сегодня ещё пусто —
     * стрик отсчитывается от вчера.
     */
    fun compute(activeDates: Set<String>, frozen: Set<String>): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        fun covered(): Boolean = fmt.format(cal.time).let { it in activeDates || it in frozen }

        if (!covered()) cal.add(Calendar.DAY_OF_YEAR, -1)
        var streak = 0
        while (covered()) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }
}
