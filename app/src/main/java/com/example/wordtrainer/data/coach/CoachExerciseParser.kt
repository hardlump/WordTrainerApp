package com.example.wordtrainer.data.coach

import com.google.gson.Gson

/**
 * Разбор ответа ИИ вида {"en": "...", "ru": "...", "explanation": "..."} в
 * упражнение «собери предложение». Устойчив к обёрткам ```json и лишнему тексту:
 * сначала вырезаем первый JSON-объект, затем парсим Gson.
 */
object CoachExerciseParser {

    private val gson = Gson()

    private data class Raw(val en: String?, val ru: String?, val explanation: String?)

    fun parse(response: String): SentenceExercise? {
        val json = extractJsonObject(response) ?: return null
        val raw = runCatching { gson.fromJson(json, Raw::class.java) }
            .getOrElse { _: Throwable -> return null }

        val en = raw?.en?.trim().orEmpty()
        val ru = raw?.ru?.trim().orEmpty()
        if (en.isBlank() || ru.isBlank()) return null

        val words = en.split(Regex("\\s+"))
            .map { it.trim().trim('.', ',', '!', '?', ';', ':', '"', '\'') }
            .filter { it.isNotEmpty() }
        if (words.size < 2) return null

        return SentenceExercise(
            question = ru,
            correctAnswer = words.joinToString(" "),
            options = words.shuffled(),
            explanation = raw?.explanation?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    /** Возвращает подстроку от первой `{` до парной ей `}` (учёт вложенности). */
    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
