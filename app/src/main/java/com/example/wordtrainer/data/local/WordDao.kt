package com.example.wordtrainer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<WordEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: WordEntity): Long

    @Update
    suspend fun update(word: WordEntity)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM words WHERE lang = :lang")
    suspend fun deleteByLang(lang: String)

    @Query("SELECT COUNT(*) FROM words WHERE lang = :lang")
    suspend fun countForLang(lang: String): Int

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getById(id: Long): WordEntity?

    @Query("SELECT * FROM words WHERE lang = :lang ORDER BY word COLLATE NOCASE ASC")
    fun observeAll(lang: String): Flow<List<WordEntity>>

    @Query(
        """
        SELECT * FROM words
        WHERE lang = :lang AND (:query = '' OR word LIKE '%' || :query || '%'
              OR translation LIKE '%' || :query || '%')
        ORDER BY word COLLATE NOCASE ASC
        """
    )
    fun search(lang: String, query: String): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE lang = :lang AND isFavorite = 1 ORDER BY word COLLATE NOCASE ASC")
    fun observeFavorites(lang: String): Flow<List<WordEntity>>

    /**
     * Слова, готовые к повторению по SRS (срок подошёл): сначала младшие коробки,
     * затем самые «просроченные». RANDOM() разрешает равенства случайно, поэтому на
     * свежей колоде (у всех box=1, nextDueAt=0) выбираются случайные слова со всего
     * словаря, а не первые по порядку добавления.
     */
    @Query(
        """
        SELECT * FROM words
        WHERE lang = :lang AND nextDueAt <= :now
        ORDER BY box ASC, nextDueAt ASC, RANDOM()
        LIMIT :limit
        """
    )
    suspend fun getDue(lang: String, now: Long, limit: Int): List<WordEntity>

    @Query("SELECT * FROM words WHERE lang = :lang ORDER BY nextDueAt ASC LIMIT :limit")
    suspend fun getNextUpcoming(lang: String, limit: Int): List<WordEntity>

    /** Случайные слова для генерации вариантов ответа в квизе. */
    @Query("SELECT * FROM words WHERE lang = :lang AND id != :excludeId ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomExcept(lang: String, excludeId: Long, limit: Int): List<WordEntity>

    @Query("SELECT COUNT(*) FROM words WHERE lang = :lang")
    fun observeTotal(lang: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE lang = :lang AND box >= :learnedBox")
    fun observeLearned(lang: String, learnedBox: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE lang = :lang AND nextDueAt <= :now")
    fun observeDueCount(lang: String, now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE lang = :lang AND nextDueAt <= :now")
    suspend fun countDueNow(lang: String, now: Long): Int
}
