package com.example.wordtrainer.domain

/**
 * Готовит предложение-«пропуск» из примера слова: находит слово в примере и
 * заменяет его на прочерк. Совпадение — по границам слова, без учёта регистра.
 *
 * Намеренно консервативно: если точного вхождения слова в примере нет (например,
 * глагол в примере спряжён — "burn the midnight oil" → "burned the midnight oil"),
 * возвращаем null. Такое слово просто не попадает в Cloze-режим — лучше пропустить,
 * чем вырезать не то слово и показать сломанное предложение.
 */
object ClozeBuilder {

    const val BLANK = "_____"

    /** Пример с вырезанным словом, либо null, если слово не найдено в примере. */
    fun build(word: String, example: String?): String? {
        if (example.isNullOrBlank()) return null
        val w = word.trim()
        if (w.isEmpty()) return null
        val regex = Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(w) + "(?![\\p{L}\\p{N}])")
        val match = regex.find(example) ?: return null
        return example.replaceRange(match.range, BLANK)
    }
}
