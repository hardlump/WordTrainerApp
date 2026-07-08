package com.example.wordtrainer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    /**
     * Insert-or-update без SQLite UPSERT (`ON CONFLICT DO UPDATE` недоступен
     * на API 24–29 со старым SQLite < 3.24): вызывающий сначала создаёт пустую
     * строку дня через [insertIgnore], затем инкрементирует через [increment].
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(stat: DailyStatEntity)

    @Query(
        """
        UPDATE daily_stats
        SET reviewed = reviewed + 1,
            correct = correct + :correctInc,
            wrong = wrong + :wrongInc
        WHERE date = :date
        """
    )
    suspend fun increment(date: String, correctInc: Int, wrongInc: Int)

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    fun observeDay(date: String): Flow<DailyStatEntity?>

    @Query("SELECT * FROM daily_stats ORDER BY date DESC")
    fun observeAllDays(): Flow<List<DailyStatEntity>>
}
