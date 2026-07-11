package com.example.wordtrainer.data

import android.content.Context
import com.example.wordtrainer.data.local.LanguageEntity
import com.example.wordtrainer.domain.Direction
import com.example.wordtrainer.domain.QuizMode
import com.example.wordtrainer.domain.TtsMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Настройки пользователя поверх SharedPreferences, но с реактивными
 * [StateFlow], чтобы UI перерисовывался при смене языка/направления и т.д.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Текущий язык хранится как код (строка); список языков живёт в БД. */
    private val _language = MutableStateFlow(prefs.getString(KEY_LANG, LanguageEntity.CODE_EN)!!)
    val language: StateFlow<String> = _language.asStateFlow()

    private val _direction = MutableStateFlow(
        Direction.valueOf(prefs.getString(KEY_DIRECTION, Direction.WORD_TO_TRANSLATION.name)!!)
    )
    val direction: StateFlow<Direction> = _direction.asStateFlow()

    private val _ttsMode = MutableStateFlow(
        TtsMode.valueOf(prefs.getString(KEY_TTS, TtsMode.OFFLINE.name)!!)
    )
    val ttsMode: StateFlow<TtsMode> = _ttsMode.asStateFlow()

    private val _dailyGoal = MutableStateFlow(prefs.getInt(KEY_GOAL, DEFAULT_GOAL))
    val dailyGoal: StateFlow<Int> = _dailyGoal.asStateFlow()

    private val _quizMode = MutableStateFlow(
        // Режим мог быть удалён из приложения (напр. CLOZE) — тогда откатываемся на CHOICE.
        runCatching { QuizMode.valueOf(prefs.getString(KEY_QUIZ_MODE, QuizMode.CHOICE.name)!!) }
            .getOrDefault(QuizMode.CHOICE)
    )
    val quizMode: StateFlow<QuizMode> = _quizMode.asStateFlow()

    // ---- Ежедневные напоминания ----
    private val _remindersEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDERS, false))
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    private val _reminderHour = MutableStateFlow(prefs.getInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR))
    val reminderHour: StateFlow<Int> = _reminderHour.asStateFlow()

    private val _reminderMinute = MutableStateFlow(prefs.getInt(KEY_REMINDER_MINUTE, 0))
    val reminderMinute: StateFlow<Int> = _reminderMinute.asStateFlow()

    fun setLanguage(code: String) {
        prefs.edit().putString(KEY_LANG, code).apply()
        _language.value = code
    }

    fun setDirection(value: Direction) {
        prefs.edit().putString(KEY_DIRECTION, value.name).apply()
        _direction.value = value
    }

    fun setTtsMode(value: TtsMode) {
        prefs.edit().putString(KEY_TTS, value.name).apply()
        _ttsMode.value = value
    }

    fun setDailyGoal(value: Int) {
        val v = value.coerceIn(1, 500)
        prefs.edit().putInt(KEY_GOAL, v).apply()
        _dailyGoal.value = v
    }

    fun setQuizMode(value: QuizMode) {
        prefs.edit().putString(KEY_QUIZ_MODE, value.name).apply()
        _quizMode.value = value
    }

    fun setRemindersEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS, value).apply()
        _remindersEnabled.value = value
    }

    fun setReminderTime(hour: Int, minute: Int) {
        prefs.edit().putInt(KEY_REMINDER_HOUR, hour).putInt(KEY_REMINDER_MINUTE, minute).apply()
        _reminderHour.value = hour
        _reminderMinute.value = minute
    }

    fun isSeeded(code: String): Boolean = prefs.getBoolean(KEY_SEEDED_PREFIX + code, false)

    fun markSeeded(code: String) {
        prefs.edit().putBoolean(KEY_SEEDED_PREFIX + code, true).apply()
    }

    private companion object {
        const val KEY_LANG = "language"
        const val KEY_DIRECTION = "direction"
        const val KEY_TTS = "tts_mode"
        const val KEY_GOAL = "daily_goal"
        const val KEY_QUIZ_MODE = "quiz_mode"
        const val KEY_SEEDED_PREFIX = "seeded_"
        const val KEY_REMINDERS = "reminders_enabled"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_REMINDER_MINUTE = "reminder_minute"
        const val DEFAULT_GOAL = 20
        const val DEFAULT_REMINDER_HOUR = 20
    }
}
