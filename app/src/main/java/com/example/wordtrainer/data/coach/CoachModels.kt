package com.example.wordtrainer.data.coach

/**
 * Модели ИИ-коуча. Формат запроса/ответа совместим с OpenAI Chat Completions
 * (локальные серверы вроде LM Studio/Ollama используют его же).
 *
 * Этот пакет намеренно изолирован от словарной части (Room, WordEntity и т.д.):
 * общение с ИИ и тренировка слов не пересекаются.
 */

data class CoachMessage(
    val role: String,   // "system" | "user" | "assistant"
    val content: String
)

data class CoachChatRequest(
    val model: String,
    val messages: List<CoachMessage>,
    val temperature: Double = 0.7
)

data class CoachChatResponse(
    val choices: List<CoachChoice> = emptyList()
)

data class CoachChoice(
    val message: CoachMessage?
)

/** Урок-диалог в стиле ролевой игры (свободное общение на заданную тему). */
data class Lesson(
    val title: String,
    val description: String,
    val systemPrompt: String,
    val isPassed: Boolean = false
)

/** Фильтр списка уроков. */
enum class LessonFilter { ALL, NOT_PASSED, PASSED }

/** Упражнение «собери предложение из слов» (генерируется ИИ). */
data class SentenceExercise(
    val question: String,       // предложение на языке перевода (русское)
    val correctAnswer: String,  // корректное предложение на изучаемом языке
    val options: List<String>,  // перемешанные слова-плитки
    val explanation: String?    // подсказка/пояснение от ИИ
)

/**
 * Источник модели ИИ:
 * - [ON_DEVICE] — модель (GGUF) запускается прямо на телефоне через llama.cpp;
 * - [LOCAL_SERVER] — HTTP к OpenAI-совместимому серверу в сети (LM Studio/Ollama на ПК).
 */
enum class CoachMode { ON_DEVICE, LOCAL_SERVER }
