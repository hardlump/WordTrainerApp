package com.example.wordtrainer.data

import android.content.Context
import com.example.wordtrainer.domain.Direction
import com.example.wordtrainer.domain.Language
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

    private val _language = MutableStateFlow(Language.fromCode(prefs.getString(KEY_LANG, Language.EN.code)!!))
    val language: StateFlow<Language> = _language.asStateFlow()

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
        QuizMode.valueOf(prefs.getString(KEY_QUIZ_MODE, QuizMode.CHOICE.name)!!)
    )
    val quizMode: StateFlow<QuizMode> = _quizMode.asStateFlow()

    fun setLanguage(value: Language) {
        prefs.edit().putString(KEY_LANG, value.code).apply()
        _language.value = value
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

    fun isSeeded(lang: Language): Boolean = prefs.getBoolean(KEY_SEEDED_PREFIX + lang.code, false)

    fun markSeeded(lang: Language) {
        prefs.edit().putBoolean(KEY_SEEDED_PREFIX + lang.code, true).apply()
    }

    private companion object {
        const val KEY_LANG = "language"
        const val KEY_DIRECTION = "direction"
        const val KEY_TTS = "tts_mode"
        const val KEY_GOAL = "daily_goal"
        const val KEY_QUIZ_MODE = "quiz_mode"
        const val KEY_SEEDED_PREFIX = "seeded_"
        const val DEFAULT_GOAL = 20
    }
}
