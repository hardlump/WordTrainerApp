package com.example.wordtrainer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DictionaryDao {

    @Query("SELECT COUNT(*) FROM dictionary")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(entries: List<DictionaryEntry>)

    /**
     * Поиск: сперва по префиксу слова (использует индекс), плюс совпадения
     * внутри слова/перевода. Пустой запрос возвращает начало словаря.
     */
    @Query(
        """
        SELECT * FROM dictionary
        WHERE :query = '' OR word LIKE :query || '%' OR word LIKE '%' || :query || '%'
              OR translation LIKE '%' || :query || '%'
        ORDER BY (CASE WHEN word LIKE :query || '%' THEN 0 ELSE 1 END), word COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int = 60): List<DictionaryEntry>
}
