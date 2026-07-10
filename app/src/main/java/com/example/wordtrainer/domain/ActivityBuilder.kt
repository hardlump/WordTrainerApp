package com.example.wordtrainer.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Готовит данные активности: линейный ряд последних дней и сетку недель для тепловой карты. */
object ActivityBuilder {

    /** Последние [days] дней (включая сегодня) с подписью-числом для столбчатого графика. */
    fun recentDays(byDate: Map<String, Int>, days: Int): List<DayActivity> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val label = SimpleDateFormat("d", Locale.US)
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(days - 1)) }
        return (0 until days).map {
            val date = fmt.format(cal.time)
            val activity = DayActivity(label.format(cal.time), byDate[date] ?: 0)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            activity
        }
    }

    /**
     * Сетка недель для тепловой карты: [weeks] столбцов (недель) по 7 строк (Пн..Вс).
     * Значение — число повторений в этот день; null — будущий день (пусто).
     */
    fun heatmap(byDate: Map<String, Int>, weeks: Int): List<List<Int?>> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = fmt.format(Calendar.getInstance().time)

        val cursor = Calendar.getInstance()
        val mondayOffset = (cursor.get(Calendar.DAY_OF_WEEK) + 5) % 7 // дней от понедельника
        cursor.add(Calendar.DAY_OF_YEAR, -mondayOffset)               // понедельник текущей недели
        cursor.add(Calendar.DAY_OF_YEAR, -(weeks - 1) * 7)            // понедельник первого столбца

        val columns = ArrayList<List<Int?>>(weeks)
        repeat(weeks) {
            val column = ArrayList<Int?>(7)
            repeat(7) {
                val date = fmt.format(cursor.time)
                column.add(if (date > today) null else (byDate[date] ?: 0))
                cursor.add(Calendar.DAY_OF_YEAR, 1)
            }
            columns.add(column)
        }
        return columns
    }
}
