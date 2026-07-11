package com.example.wordtrainer.data.coach

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Хранилище истории свободного чата и списка пройденных уроков ИИ-коуча.
 * Отдельный prefs-файл — данные ИИ не смешиваются со словарём.
 */
class CoachHistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("coach_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun loadChat(): List<CoachMessage> {
        val json = prefs.getString(KEY_CHAT, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<CoachMessage>>(
                json, object : TypeToken<List<CoachMessage>>() {}.type
            )
        }.getOrDefault(emptyList())
    }

    fun saveChat(messages: List<CoachMessage>) {
        prefs.edit().putString(KEY_CHAT, gson.toJson(messages)).apply()
    }

    fun clearChat() {
        prefs.edit().remove(KEY_CHAT).apply()
    }

    fun passedLessons(): Set<String> =
        prefs.getStringSet(KEY_PASSED, emptySet())?.toSet() ?: emptySet()

    fun markPassed(title: String) {
        val updated = passedLessons().toMutableSet().apply { add(title) }
        prefs.edit().putStringSet(KEY_PASSED, updated).apply()
    }

    private companion object {
        const val KEY_CHAT = "chat_history"
        const val KEY_PASSED = "passed_lessons"
    }
}
