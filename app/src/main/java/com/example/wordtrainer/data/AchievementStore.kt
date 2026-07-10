package com.example.wordtrainer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Прогресс достижений и заморозок стрика поверх SharedPreferences.
 * Не в БД — чтобы не менять схему Room и не рисковать сбросом данных.
 */
class AchievementStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("achievements", Context.MODE_PRIVATE)

    /** id ачивки -> момент разблокировки. */
    private val _unlocked = MutableStateFlow(loadUnlocked())
    val unlocked: StateFlow<Map<String, Long>> = _unlocked.asStateFlow()

    private val _freezes = MutableStateFlow(prefs.getInt(KEY_FREEZES, DEFAULT_FREEZES))
    val freezes: StateFlow<Int> = _freezes.asStateFlow()

    private val _frozenDates = MutableStateFlow(prefs.getStringSet(KEY_FROZEN, emptySet())!!.toSet())
    val frozenDates: StateFlow<Set<String>> = _frozenDates.asStateFlow()

    var lastFreezeMilestone: Int
        get() = prefs.getInt(KEY_MILESTONE, 0)
        set(value) { prefs.edit().putInt(KEY_MILESTONE, value).apply() }

    /** Разблокирует ачивку. true — если это произошло впервые. */
    fun unlock(id: String): Boolean {
        if (_unlocked.value.containsKey(id)) return false
        val map = _unlocked.value.toMutableMap()
        map[id] = System.currentTimeMillis()
        _unlocked.value = map
        saveUnlocked(map)
        return true
    }

    fun setFreezes(count: Int) {
        val v = count.coerceIn(0, 99)
        prefs.edit().putInt(KEY_FREEZES, v).apply()
        _freezes.value = v
    }

    fun setFrozenDates(dates: Set<String>) {
        val copy = dates.toSet()
        prefs.edit().putStringSet(KEY_FROZEN, copy).apply()
        _frozenDates.value = copy
    }

    fun incDictionaryAddCount(): Int {
        val n = prefs.getInt(KEY_DICT, 0) + 1
        prefs.edit().putInt(KEY_DICT, n).apply()
        return n
    }

    private fun loadUnlocked(): Map<String, Long> {
        val json = prefs.getString(KEY_UNLOCKED, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, obj.getLong(k))
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveUnlocked(map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_UNLOCKED, obj.toString()).apply()
    }

    private companion object {
        const val KEY_UNLOCKED = "unlocked"
        const val KEY_FREEZES = "freezes"
        const val KEY_FROZEN = "frozen_dates"
        const val KEY_MILESTONE = "freeze_milestone"
        const val KEY_DICT = "dict_add_count"
        const val DEFAULT_FREEZES = 1
    }
}
