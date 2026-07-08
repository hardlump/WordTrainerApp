package com.example.wordtrainer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Язык (колода) пользователя. Раньше языки были захардкожены в enum, теперь
 * список хранится в БД и пополняется пользователем.
 *
 * @param code   стабильный идентификатор, которым помечаются слова ([WordEntity.lang]).
 * @param name   отображаемое название, напр. «English», «Deutsch».
 * @param locale BCP-47 тег для озвучки (TTS), напр. «en», «uk», «de».
 * @param builtIn встроенный язык (EN/UA) — есть стартовые данные/защита от случайного удаления.
 */
@Entity(tableName = "languages")
data class LanguageEntity(
    @PrimaryKey val code: String,
    val name: String,
    val locale: String,
    val position: Int = 0,
    val builtIn: Boolean = false
) {
    companion object {
        const val CODE_EN = "EN"
        const val CODE_UA = "UA"

        /** Языки по умолчанию при первом запуске. */
        val defaults = listOf(
            LanguageEntity(CODE_EN, "English", "en", position = 0, builtIn = true),
            LanguageEntity(CODE_UA, "Українська", "uk", position = 1, builtIn = true)
        )
    }
}
