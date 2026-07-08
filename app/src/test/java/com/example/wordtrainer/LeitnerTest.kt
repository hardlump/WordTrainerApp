package com.example.wordtrainer

import com.example.wordtrainer.data.local.WordEntity
import com.example.wordtrainer.domain.Leitner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeitnerTest {

    private fun word(box: Int) = WordEntity(id = 1, word = "cat", translation = "кот", lang = "EN", box = box)

    @Test
    fun `correct answer promotes box and schedules later`() {
        val now = 1_000_000L
        val result = Leitner.onAnswer(word(box = 1), correct = true, now = now)

        assertEquals(2, result.box)
        assertEquals(1, result.correctCount)
        assertEquals(now + Leitner.intervalFor(2), result.nextDueAt)
        assertEquals(now, result.lastReviewedAt)
    }

    @Test
    fun `box never exceeds max`() {
        val result = Leitner.onAnswer(word(box = Leitner.MAX_BOX), correct = true)
        assertEquals(Leitner.MAX_BOX, result.box)
    }

    @Test
    fun `wrong answer resets to first box and is due immediately`() {
        val now = 5_000_000L
        val result = Leitner.onAnswer(word(box = 4), correct = false, now = now)

        assertEquals(1, result.box)
        assertEquals(1, result.wrongCount)
        assertEquals(now, result.nextDueAt) // интервал первой коробки = 0
    }

    @Test
    fun `intervals grow with box number`() {
        assertEquals(0L, Leitner.intervalFor(1))
        assertTrue(Leitner.intervalFor(2) < Leitner.intervalFor(3))
        assertTrue(Leitner.intervalFor(3) < Leitner.intervalFor(5))
    }
}
