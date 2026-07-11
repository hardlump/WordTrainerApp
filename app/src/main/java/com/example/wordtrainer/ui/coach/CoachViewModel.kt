package com.example.wordtrainer.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wordtrainer.data.coach.CoachContent
import com.example.wordtrainer.data.coach.CoachHistoryStore
import com.example.wordtrainer.data.coach.CoachMessage
import com.example.wordtrainer.data.coach.CoachMode
import com.example.wordtrainer.data.coach.CoachRepository
import com.example.wordtrainer.data.coach.CoachSettingsStore
import com.example.wordtrainer.data.coach.Lesson
import com.example.wordtrainer.data.coach.LessonFilter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Экран ИИ: свободный чат или активный урок-диалог. */
data class CoachChatState(
    val messages: List<CoachMessage> = emptyList(), // видимые (без system)
    val isLoading: Boolean = false,
    val inLesson: Boolean = false,
    val lessonTitle: String = "",
    val progress: Float = 0f,
    val finished: Boolean = false,
    val playing: String? = null
)

/**
 * Общий ViewModel ИИ-коуча: свободный чат + активные уроки-диалоги.
 * Список уроков (поиск/фильтр/прогресс) обслуживается этим же VM без реактивности —
 * фрагмент списка пересоздаётся при каждом показе.
 */
class CoachViewModel(
    private val repo: CoachRepository,
    private val settings: CoachSettingsStore,
    private val history: CoachHistoryStore
) : ViewModel() {

    private val _state = MutableStateFlow(CoachChatState())
    val state: StateFlow<CoachChatState> = _state.asStateFlow()

    /** Просьба озвучить ответ ИИ (activity подхватывает и отдаёт в TTS). */
    private val _speak = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val speak: SharedFlow<String> = _speak.asSharedFlow()

    /** Одноразовые ошибки для показа Toast/Snackbar (в историю не пишем). */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val chatMessages = mutableListOf<CoachMessage>()
    private val lessonMessages = mutableListOf<CoachMessage>()

    private var activeLesson: Lesson? = null
    private val inLesson get() = activeLesson != null

    init {
        chatMessages.addAll(history.loadChat())
        if (chatMessages.none { it.role == "system" }) {
            chatMessages.add(0, CoachMessage("system", settings.systemPrompt.value))
        }
        emit()
    }

    // ---- Отправка сообщений -------------------------------------------------

    fun sendText(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || (inLesson && isLessonFinished())) return
        val target = if (inLesson) lessonMessages else chatMessages
        if (target.isEmpty()) {
            val prompt = activeLesson?.systemPrompt ?: settings.systemPrompt.value
            target.add(CoachMessage("system", prompt))
        }
        target.add(CoachMessage("user", clean))
        _state.value = _state.value.copy(isLoading = true)
        emit()

        viewModelScope.launch {
            try {
                val toSend = target.toMutableList()
                if (inLesson && target.count { it.role != "system" } >= MAX_LESSON_MESSAGES - 1) {
                    val last = toSend.last()
                    toSend[toSend.lastIndex] = last.copy(content = last.content + FINAL_PROMPT)
                }
                val reply = repo.complete(toSend)
                if (reply.isNotEmpty()) {
                    target.add(CoachMessage("assistant", reply))
                    if (!inLesson) history.saveChat(chatMessages)
                    _speak.tryEmit(reply)
                }
            } catch (e: Exception) {
                _errors.tryEmit(e.localizedMessage ?: "network error")
            } finally {
                _state.value = _state.value.copy(isLoading = false)
                emit()
            }
        }
    }

    /** Голосовой ввод: сперва расставляем пунктуацию, затем отправляем как обычно. */
    fun sendVoice(rawText: String) {
        val raw = rawText.trim()
        if (raw.isBlank()) return
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val fixed = try {
                repo.fixPunctuation(formatVoice(raw))
            } catch (e: Exception) {
                formatVoice(raw)
            }
            sendText(fixed)
        }
    }

    private fun formatVoice(input: String): String {
        var text = input.trim().replace(Regex("\\s+"), " ")
        text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        if (!text.endsWith(".") && !text.endsWith("!") && !text.endsWith("?")) text += "."
        return text
    }

    // ---- Уроки-диалоги ------------------------------------------------------

    fun startLesson(lesson: Lesson) {
        lessonMessages.clear()
        val strict = """
            ${lesson.systemPrompt}
            TEACHING RULES:
            1. Be extremely concise (1-3 sentences).
            2. Ask exactly ONE question to the student.
            3. Stay on topic: ${lesson.title}.
            4. After $MAX_LESSON_MESSAGES messages, write "LESSON COMPLETE" and summarize my mistakes.
        """.trimIndent()
        lessonMessages.add(CoachMessage("system", strict))
        lessonMessages.add(
            CoachMessage("assistant", "Hello! Let's start: ${lesson.title}. What do you know about this?")
        )
        activeLesson = lesson
        emit()
    }

    fun finishLesson() {
        activeLesson?.let { history.markPassed(it.title) }
        activeLesson = null
        lessonMessages.clear()
        emit()
    }

    /** Выход из урока без отметки о прохождении (кнопка «назад»). */
    fun leaveLesson() {
        activeLesson = null
        lessonMessages.clear()
        emit()
    }

    private fun isLessonFinished(): Boolean = lessonProgress() >= 1f

    private fun lessonProgress(): Float {
        if (!inLesson) return 0f
        val dialogue = lessonMessages.count { it.role != "system" }
        return ((dialogue - 1).coerceAtLeast(0).toFloat() / (MAX_LESSON_MESSAGES - 1)).coerceAtMost(1f)
    }

    // ---- Список уроков ------------------------------------------------------

    fun filteredLessons(query: String, filter: LessonFilter): List<Lesson> {
        val passed = history.passedLessons()
        val base = CoachContent.lessons.map { it.copy(isPassed = passed.contains(it.title)) }
        val byStatus = when (filter) {
            LessonFilter.ALL -> base
            LessonFilter.NOT_PASSED -> base.filter { !it.isPassed }
            LessonFilter.PASSED -> base.filter { it.isPassed }
        }
        return if (query.isBlank()) byStatus
        else byStatus.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }

    fun count(filter: LessonFilter): Int {
        val passed = history.passedLessons()
        return when (filter) {
            LessonFilter.ALL -> CoachContent.lessons.size
            LessonFilter.NOT_PASSED -> CoachContent.lessons.count { !passed.contains(it.title) }
            LessonFilter.PASSED -> passed.size
        }
    }

    fun randomLesson(query: String, filter: LessonFilter): Lesson? =
        filteredLessons(query, filter).randomOrNull()

    // ---- Прочее -------------------------------------------------------------

    fun setPlaying(text: String?) {
        _state.value = _state.value.copy(playing = text)
    }

    fun clearHistory() {
        chatMessages.clear()
        chatMessages.add(CoachMessage("system", settings.systemPrompt.value))
        history.clearChat()
        emit()
    }

    private fun emit() {
        val source = if (inLesson) lessonMessages else chatMessages
        _state.value = _state.value.copy(
            messages = source.filter { it.role != "system" },
            inLesson = inLesson,
            lessonTitle = activeLesson?.title ?: "",
            progress = lessonProgress(),
            finished = inLesson && isLessonFinished()
        )
    }

    private companion object {
        const val MAX_LESSON_MESSAGES = 15
        const val FINAL_PROMPT =
            "\n\n[SYSTEM CRITICAL INSTRUCTION]: This is your LAST response. 1. Do NOT ask any more questions. " +
                "2. Write \"LESSON COMPLETE\" in capital letters. 3. Summarize my vocabulary and grammar mistakes. " +
                "4. Say goodbye."
    }
}
