package com.example.wordtrainer.domain

import com.example.wordtrainer.data.local.WordEntity

/**
 * Система интервального повторения Лейтнера.
 *
 * Коробки 1..5. Правильный ответ переводит слово в следующую коробку и
 * увеличивает интервал до следующего показа; ошибка сбрасывает в коробку 1.
 * Слово считается выученным, когда доходит до [LEARNED_BOX].
 */
object Leitner {

    const val MAX_BOX = 5
    const val LEARNED_BOX = 5

    /** Интервалы для коробок 1..5 в миллисекундах. Индекс = box - 1. */
    private val INTERVALS_MS = longArrayOf(
        0L,                       // box 1 — сразу
        1L * 24 * 60 * 60 * 1000, // box 2 — через 1 день
        3L * 24 * 60 * 60 * 1000, // box 3 — через 3 дня
        7L * 24 * 60 * 60 * 1000, // box 4 — через неделю
        16L * 24 * 60 * 60 * 1000 // box 5 — через ~2.5 недели
    )

    fun intervalFor(box: Int): Long {
        val idx = (box - 1).coerceIn(0, INTERVALS_MS.lastIndex)
        return INTERVALS_MS[idx]
    }

    /** Возвращает копию слова с обновлённым состоянием SRS после ответа. */
    fun onAnswer(word: WordEntity, correct: Boolean, now: Long = System.currentTimeMillis()): WordEntity {
        val newBox = if (correct) (word.box + 1).coerceAtMost(MAX_BOX) else 1
        return word.copy(
            box = newBox,
            correctCount = word.correctCount + if (correct) 1 else 0,
            wrongCount = word.wrongCount + if (correct) 0 else 1,
            lastReviewedAt = now,
            nextDueAt = now + intervalFor(newBox)
        )
    }
}
