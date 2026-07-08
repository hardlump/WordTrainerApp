package com.example.wordtrainer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.wordtrainer.data.local.AppDatabase
import com.example.wordtrainer.data.local.WordDao
import com.example.wordtrainer.data.local.WordEntity
import com.example.wordtrainer.domain.Leitner
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WordDao

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        dao = db.wordDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun dueQueryReturnsOnlyPastDueWords() = runTest {
        val now = System.currentTimeMillis()
        dao.insertAll(
            listOf(
                WordEntity(word = "due", translation = "срок", lang = "EN", nextDueAt = now - 1000),
                WordEntity(word = "future", translation = "будущее", lang = "EN", nextDueAt = now + 100_000)
            )
        )
        val due = dao.getDue("EN", now, limit = 10)
        assertEquals(1, due.size)
        assertEquals("due", due.first().word)
    }

    @Test
    fun uniqueIndexIgnoresDuplicates() = runTest {
        val w = WordEntity(word = "cat", translation = "кот", lang = "EN")
        dao.insert(w)
        dao.insert(w.copy(id = 0))
        assertEquals(1, dao.countForLang("EN"))
    }

    @Test
    fun srsUpdatePersistsNewBox() = runTest {
        val id = dao.insert(WordEntity(word = "cat", translation = "кот", lang = "EN"))
        val stored = dao.getById(id)!!
        dao.update(Leitner.onAnswer(stored, correct = true))
        assertTrue(dao.getById(id)!!.box > stored.box)
    }
}
