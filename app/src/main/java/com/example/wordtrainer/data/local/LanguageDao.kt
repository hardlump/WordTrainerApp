package com.example.wordtrainer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LanguageDao {

    @Query("SELECT * FROM languages ORDER BY position ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<LanguageEntity>>

    @Query("SELECT * FROM languages ORDER BY position ASC, name COLLATE NOCASE ASC")
    suspend fun getAll(): List<LanguageEntity>

    @Query("SELECT * FROM languages WHERE code = :code")
    suspend fun getByCode(code: String): LanguageEntity?

    @Query("SELECT COUNT(*) FROM languages")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(language: LanguageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(languages: List<LanguageEntity>)

    @Query("DELETE FROM languages WHERE code = :code")
    suspend fun delete(code: String)
}
