package com.example.wordtrainer.domain

/** Язык изучаемой колоды. */
enum class Language(val code: String, val title: String) {
    EN("EN", "EN (English)"),
    UA("UA", "UA (Українська)");

    companion object {
        fun fromCode(code: String): Language = entries.firstOrNull { it.code == code } ?: EN
    }
}

/** Направление тренировки. */
enum class Direction {
    /** Показываем слово, вспоминаем перевод. */
    WORD_TO_TRANSLATION,

    /** Показываем перевод, вспоминаем слово. */
    TRANSLATION_TO_WORD;

    fun toggled(): Direction =
        if (this == WORD_TO_TRANSLATION) TRANSLATION_TO_WORD else WORD_TO_TRANSLATION
}

/** Режим озвучки. */
enum class TtsMode { OFFLINE, ONLINE }
