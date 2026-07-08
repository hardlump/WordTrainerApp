package com.example.wordtrainer.data

import android.content.Context
import android.net.Uri
import com.example.wordtrainer.data.local.AppDatabase
import com.example.wordtrainer.data.local.DailyStatEntity
import com.example.wordtrainer.data.local.WordEntity
import com.example.wordtrainer.domain.DayActivity
import com.example.wordtrainer.domain.Language
import com.example.wordtrainer.domain.Leitner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Единая точка доступа к данным. Вся работа с диском/БД — на [Dispatchers.IO],
 * поэтому UI-поток больше не блокируется загрузкой словаря.
 */
class WordRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.get(context)
) {
    private val wordDao = db.wordDao()
    private val statsDao = db.statsDao()
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ---- Сидинг из ассетов при первом запуске ----------------------------

    suspend fun seedIfNeeded(lang: Language, settings: SettingsStore) = withContext(Dispatchers.IO) {
        if (settings.isSeeded(lang)) return@withContext
        if (wordDao.countForLang(lang.code) == 0) {
            val seed = when (lang) {
                Language.EN -> readAssetJsonWords("words.json", lang)
                Language.UA -> emptyList() // данных для UA пока нет — пустое состояние + импорт
            }
            if (seed.isNotEmpty()) wordDao.insertAll(seed)
        }
        settings.markSeeded(lang)
    }

    private fun readAssetJsonWords(assetName: String, lang: Language): List<WordEntity> = try {
        val json = context.assets.open(assetName)
            .bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        parseJsonWords(json, lang)
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    private fun parseJsonWords(json: String, lang: Language): List<WordEntity> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        val list = ArrayList<WordEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.has("word") && obj.has("translation")) {
                val w = obj.getString("word").trim()
                val t = obj.getString("translation").trim()
                if (w.isNotEmpty() && t.isNotEmpty()) list.add(WordEntity(word = w, translation = t, lang = lang.code))
            }
        }
        return list
    }

    // ---- Реактивные списки ----------------------------------------------

    fun observeWords(lang: Language, query: String): Flow<List<WordEntity>> =
        wordDao.search(lang.code, query.trim())

    fun observeFavorites(lang: Language): Flow<List<WordEntity>> =
        wordDao.observeFavorites(lang.code)

    fun observeTotal(lang: Language): Flow<Int> = wordDao.observeTotal(lang.code)

    fun observeLearned(lang: Language): Flow<Int> =
        wordDao.observeLearned(lang.code, Leitner.LEARNED_BOX)

    fun observeDueCount(lang: Language): Flow<Int> =
        wordDao.observeDueCount(lang.code, System.currentTimeMillis())

    // ---- Тренировка ------------------------------------------------------

    /** Порция для карточек: сперва «подошедшие» по SRS, иначе ближайшие. */
    suspend fun nextTrainingBatch(lang: Language, limit: Int = 30): List<WordEntity> =
        withContext(Dispatchers.IO) {
            val due = wordDao.getDue(lang.code, System.currentTimeMillis(), limit)
            if (due.isNotEmpty()) due.shuffled()
            else wordDao.getNextUpcoming(lang.code, limit).shuffled()
        }

    /** Дистракторы (неправильные варианты) для квиза. */
    suspend fun distractorsFor(word: WordEntity, count: Int = 3): List<WordEntity> =
        withContext(Dispatchers.IO) {
            wordDao.getRandomExcept(word.lang, word.id, count)
        }

    /**
     * Регистрирует ответ: обновляет SRS слова и дневную статистику.
     * Возвращает обновлённое слово (по нему можно понять, стало ли оно
     * выученным — коробка достигла [Leitner.LEARNED_BOX]).
     */
    suspend fun submitAnswer(word: WordEntity, correct: Boolean): WordEntity = withContext(Dispatchers.IO) {
        val updated = Leitner.onAnswer(word, correct)
        wordDao.update(updated)
        val day = today()
        statsDao.insertIgnore(DailyStatEntity(day))
        statsDao.increment(day, if (correct) 1 else 0, if (correct) 0 else 1)
        updated
    }

    suspend fun toggleFavorite(word: WordEntity) = withContext(Dispatchers.IO) {
        wordDao.update(word.copy(isFavorite = !word.isFavorite))
    }

    suspend fun addWord(word: String, translation: String, lang: Language): Boolean =
        withContext(Dispatchers.IO) {
            val w = word.trim()
            val t = translation.trim()
            if (w.isEmpty() || t.isEmpty()) return@withContext false
            wordDao.insert(WordEntity(word = w, translation = t, lang = lang.code)) != -1L
        }

    suspend fun deleteWord(word: WordEntity) = withContext(Dispatchers.IO) {
        wordDao.deleteById(word.id)
    }

    // ---- Импорт / экспорт ------------------------------------------------

    /** Импорт JSON-массива `[{"word":..,"translation":..}]` из выбранного файла. */
    suspend fun importJson(uri: Uri, lang: Language): Int = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(StandardCharsets.UTF_8).readText()
        } ?: return@withContext 0
        val words = parseJsonWords(json, lang)
        if (words.isEmpty()) return@withContext 0
        val ids = wordDao.insertAll(words)
        ids.count { it != -1L } // сколько реально добавилось (без дублей)
    }

    /** Экспорт колоды текущего языка в JSON-строку. */
    suspend fun exportJson(lang: Language): String = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        wordDao.getNextUpcoming(lang.code, Int.MAX_VALUE).sortedBy { it.word }.forEach { w ->
            arr.put(JSONObject().put("word", w.word).put("translation", w.translation))
        }
        arr.toString(2)
    }

    // ---- Статистика ------------------------------------------------------

    fun observeToday(): Flow<DailyStatEntity> =
        statsDao.observeDay(today()).map { it ?: DailyStatEntity(today()) }

    /** Стрик — количество последовательных дней (включая сегодня) с активностью. */
    fun observeStreak(): Flow<Int> = statsDao.observeAllDays().map { days ->
        val activeDates = days.filter { it.reviewed > 0 }.map { it.date }.toHashSet()
        if (activeDates.isEmpty()) return@map 0
        val cal = Calendar.getInstance()
        // если сегодня ещё не занимались — стрик считаем от вчера
        if (!activeDates.contains(dayFormat.format(cal.time))) cal.add(Calendar.DAY_OF_YEAR, -1)
        var streak = 0
        while (activeDates.contains(dayFormat.format(cal.time))) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        streak
    }

    /** Активность за последние [days] дней (включая сегодня), пустые дни = 0. */
    fun observeActivity(days: Int = 14): Flow<List<DayActivity>> =
        statsDao.observeAllDays().map { rows ->
            val reviewedByDate = rows.associate { it.date to it.reviewed }
            val labelFormat = SimpleDateFormat("d", Locale.US)
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
            (0 until days).map {
                val date = dayFormat.format(cal.time)
                val activity = DayActivity(labelFormat.format(cal.time), reviewedByDate[date] ?: 0)
                cal.add(Calendar.DAY_OF_YEAR, 1)
                activity
            }
        }

    private fun today(): String = dayFormat.format(Calendar.getInstance().time)
}
