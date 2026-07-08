package com.example.wordtrainer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Агрегат по одному дню (дата в формате yyyy-MM-dd) для стрика и графика. */
@Entity(tableName = "daily_stats")
data class DailyStatEntity(
    @PrimaryKey val date: String,
    val reviewed: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0
)
