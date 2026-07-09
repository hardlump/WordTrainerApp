package com.example.wordtrainer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Запись большого справочного словаря (Мюллер, ~56k). Хранится отдельно от
 * учебной колоды [WordEntity]: словарь — источник, из которого слова
 * добавляются в обучение. Индекс по [word] нужен для быстрого поиска по префиксу.
 */
@Entity(
    tableName = "dictionary",
    indices = [Index(value = ["word"])]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val transcription: String?,
    val partOfSpeech: String?,
    val translation: String
)
