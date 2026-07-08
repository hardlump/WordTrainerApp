package com.example.wordtrainer.data

import android.content.Context
import android.net.Uri
import com.example.wordtrainer.data.local.AppDatabase
import com.example.wordtrainer.data.local.DailyStatEntity
import com.example.wordtrainer.data.local.LanguageEntity
import com.example.wordtrainer.data.local.WordEntity
import com.example.wordtrainer.domain.DayActivity
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
 *
 * Языки хранятся в БД (таблица languages) и обозначаются кодом-строкой,
 * которым помечены слова ([WordEntity.lang]).
 */
class WordRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.get(context)
) {
    private val wordDao = db.wordDao()
    private val statsDao = db.statsDao()
    private val languageDao = db.languageDao()
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ---- Языки -----------------------------------------------------------

    /** Создаёт языки по умолчанию (EN/UA) при первом запуске. */
    suspend fun ensureDefaultLanguages() = withContext(Dispatchers.IO) {
        if (languageDao.count() == 0) languageDao.insertAll(LanguageEntity.defaults)
    }

    fun observeLanguages(): Flow<List<LanguageEntity>> = languageDao.observeAll()

    suspend fun getLanguage(code: String): LanguageEntity? = withContext(Dispatchers.IO) {
        languageDao.getByCode(code)
    }

    /** Добавляет пользовательский язык. Возвращает его код или null при пустых полях. */
    suspend fun addLanguage(name: String, locale: String): String? = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val cleanLocale = locale.trim()
        if (cleanName.isEmpty() || cleanLocale.isEmpty()) return@withContext null
        val existing = languageDao.getAll()
        val code = generateCode(cleanLocale, existing.map { it.code }.toSet())
        val position = (existing.maxOfOrNull { it.position } ?: 0) + 1
        languageDao.insert(LanguageEntity(code, cleanName, cleanLocale, position, builtIn = false))
        // У нового языка нет стартовых данных — считаем его сразу «засеянным».
        code
    }

    /** Удаляет язык вместе со всеми его словами. Последний язык удалить нельзя. */
    suspend fun deleteLanguage(code: String): Boolean = withContext(Dispatchers.IO) {
        if (languageDao.count() <= 1) return@withContext false
        wordDao.deleteByLang(code)
        languageDao.delete(code)
        true
    }

    private fun generateCode(locale: String, taken: Set<String>): String {
        val base = locale.uppercase().filter { it.isLetterOrDigit() }.take(6).ifEmpty { "LANG" }
        if (base !in taken) return base
        var i = 2
        while ("$base$i" in taken) i++
        return "$base$i"
    }

    // ---- Сидинг из ассетов при первом запуске ----------------------------

    suspend fun seedIfNeeded(code: String, settings: SettingsStore) = withContext(Dispatchers.IO) {
        if (settings.isSeeded(code)) return@withContext
        // Стартовые данные есть только для встроенного английского.
        if (code == LanguageEntity.CODE_EN && wordDao.countForLang(code) == 0) {
            val seed = readAssetJsonWords("words.json", code)
            if (seed.isNotEmpty()) wordDao.insertAll(seed)
        }
        settings.markSeeded(code)
    }

    private fun readAssetJsonWords(assetName: String, code: String): List<WordEntity> = try {
        val json = context.assets.open(assetName)
            .bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        parseJsonWords(json, code)
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    private fun parseJsonWords(json: String, code: String): List<WordEntity> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        val list = ArrayList<WordEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.has("word") && obj.has("translation")) {
                val w = obj.getString("word").trim()
                val t = obj.getString("translation").trim()
                if (w.isNotEmpty() && t.isNotEmpty()) list.add(WordEntity(word = w, translation = t, lang = code))
            }
        }
        return list
    }

    // ---- Реактивные списки ----------------------------------------------

    fun observeWords(code: String, query: String): Flow<List<WordEntity>> =
        wordDao.search(code, query.trim())

    fun observeFavorites(code: String): Flow<List<WordEntity>> = wordDao.observeFavorites(code)

    fun observeTotal(code: String): Flow<Int> = wordDao.observeTotal(code)

    fun observeLearned(code: String): Flow<Int> = wordDao.observeLearned(code, Leitner.LEARNED_BOX)

    fun observeDueCount(code: String): Flow<Int> =
        wordDao.observeDueCount(code, System.currentTimeMillis())

    // ---- Тренировка ------------------------------------------------------

    /** Порция для карточек: сперва «подошедшие» по SRS, иначе ближайшие. */
    suspend fun nextTrainingBatch(code: String, limit: Int = 30): List<WordEntity> =
        withContext(Dispatchers.IO) {
            val due = wordDao.getDue(code, System.currentTimeMillis(), limit)
            if (due.isNotEmpty()) due.shuffled()
            else wordDao.getNextUpcoming(code, limit).shuffled()
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

    suspend fun addWord(word: String, translation: String, code: String): Boolean =
        withContext(Dispatchers.IO) {
            val w = word.trim()
            val t = translation.trim()
            if (w.isEmpty() || t.isEmpty()) return@withContext false
            wordDao.insert(WordEntity(word = w, translation = t, lang = code)) != -1L
        }

    suspend fun deleteWord(word: WordEntity) = withContext(Dispatchers.IO) {
        wordDao.deleteById(word.id)
    }

    // ---- Импорт / экспорт ------------------------------------------------

    /** Импорт JSON-массива `[{"word":..,"translation":..}]` из выбранного файла. */
    suspend fun importJson(uri: Uri, code: String): Int = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(StandardCharsets.UTF_8).readText()
        } ?: return@withContext 0
        val words = parseJsonWords(json, code)
        if (words.isEmpty()) return@withContext 0
        val ids = wordDao.insertAll(words)
        ids.count { it != -1L } // сколько реально добавилось (без дублей)
    }

    /** Экспорт колоды текущего языка в JSON-строку. */
    suspend fun exportJson(code: String): String = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        wordDao.getNextUpcoming(code, Int.MAX_VALUE).sortedBy { it.word }.forEach { w ->
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
