package com.example.wordtrainer.domain

/** Активность за один день для графика на экране статистики. */
data class DayActivity(
    val label: String, // подпись оси X, напр. число месяца
    val count: Int      // сколько слов повторено в этот день
)
