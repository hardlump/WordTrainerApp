package com.example.wordtrainer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Одно слово в колоде. Поля SRS (box/nextDueAt) реализуют систему Лейтнера:
 * коробки 1..5, при правильном ответе слово переходит в следующую коробку
 * и показывается реже, при ошибке возвращается в первую.
 */
@Entity(
    tableName = "words",
    indices = [Index(value = ["word", "translation", "lang"], unique = true)]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val translation: String,
    val lang: String,
    // Необязательные поля «богатой» карточки (могут отсутствовать в старых данных).
    val transcription: String? = null,
    val partOfSpeech: String? = null,
    val example: String? = null,
    val box: Int = 1,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val isFavorite: Boolean = false,
    val lastReviewedAt: Long? = null,
    val nextDueAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
