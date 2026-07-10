package com.example.wordtrainer.domain

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

/** Тип тренировки в квизе. */
enum class QuizMode {
    /** Выбор правильного перевода из вариантов. */
    CHOICE,

    /** Ввод ответа с клавиатуры. */
    INPUT,

    /** На слух: озвучивается слово, вводится перевод. */
    LISTENING
}
