package com.example.wordtrainer.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wordtrainer.data.AchievementManager
import com.example.wordtrainer.data.SettingsStore
import com.example.wordtrainer.data.WordRepository
import com.example.wordtrainer.data.local.WordEntity
import com.example.wordtrainer.domain.Direction
import com.example.wordtrainer.domain.Leitner
import com.example.wordtrainer.domain.QuizMode
import com.example.wordtrainer.domain.TtsMode
import com.example.wordtrainer.tts.Speaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuizOption(val word: WordEntity, val text: String)

data class QuizState(
    val target: WordEntity? = null,
    val prompt: String = "",
    val options: List<QuizOption> = emptyList(),
    val direction: Direction = Direction.WORD_TO_TRANSLATION,
    val mode: QuizMode = QuizMode.CHOICE,
    val answeredId: Long? = null,     // CHOICE: выбранный вариант
    val correctId: Long? = null,      // CHOICE: правильный вариант
    val inputAnswered: Boolean = false, // INPUT: ответ отправлен
    val inputCorrect: Boolean = false,  // INPUT: верно ли
    val expectedAnswer: String = "",    // правильный ответ (для показа при ошибке)
    val correctCount: Int = 0,
    val totalCount: Int = 0,
    val sessionLearned: Int = 0,        // сколько слов выучено за сессию
    val loading: Boolean = true,
    val notEnough: Boolean = false,     // мало слов для текущего режима
    val finished: Boolean = false       // сессия завершена — показываем итог
) {
    val answered: Boolean get() = answeredId != null || inputAnswered
}

class QuizViewModel(
    private val repo: WordRepository,
    private val settings: SettingsStore,
    private val achievements: AchievementManager,
    private val speaker: Speaker
) : ViewModel() {

    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state.asStateFlow()

    /** Начать новую сессию с обнулением счёта. */
    fun start() {
        _state.value = QuizState(loading = true, mode = settings.quizMode.value)
        nextQuestion(resetScore = true)
    }

    fun setMode(mode: QuizMode) {
        if (mode == settings.quizMode.value) return
        settings.setQuizMode(mode)
        start()
    }

    fun nextQuestion(resetScore: Boolean = false) {
        viewModelScope.launch {
            val lang = settings.language.value
            repo.seedIfNeeded(lang, settings)
            val mode = settings.quizMode.value
            val target = repo.nextTrainingBatch(lang, limit = 1).firstOrNull()
            if (target == null) {
                _state.value = _state.value.copy(loading = false, notEnough = true, target = null, finished = false)
                return@launch
            }
            val direction = settings.direction.value
            val listening = mode == QuizMode.LISTENING
            // На слух: звучит слово, ждём перевод (направление не влияет).
            val expected = if (listening) target.translation else answerText(target, direction)

            val options: List<QuizOption>
            if (mode == QuizMode.CHOICE) {
                val distractors = repo.distractorsFor(target, count = 3)
                if (distractors.isEmpty()) {
                    // Для выбора нужно ≥2 слов; ввод/слух работают и с одним.
                    _state.value = _state.value.copy(loading = false, notEnough = true, target = null)
                    return@launch
                }
                options = (distractors + target).map { QuizOption(it, answerText(it, direction)) }.shuffled()
            } else {
                options = emptyList()
            }

            val prev = _state.value
            _state.value = QuizState(
                target = target,
                // В режиме «на слух» prompt хранит само слово (для кнопки «Показать слово»).
                prompt = if (listening) target.word else promptText(target, direction),
                options = options,
                direction = direction,
                mode = mode,
                correctId = target.id,
                expectedAnswer = expected,
                correctCount = if (resetScore) 0 else prev.correctCount,
                totalCount = if (resetScore) 0 else prev.totalCount,
                sessionLearned = if (resetScore) 0 else prev.sessionLearned,
                loading = false
            )

            if (listening) speakWord()
        }
    }

    /** Проигрывает текущее слово (автоматически на новом вопросе и по кнопке «Повторить»). */
    fun replay() = speakWord()

    private fun speakWord() {
        val word = _state.value.target ?: return
        viewModelScope.launch {
            val locale = repo.getLanguage(settings.language.value)?.locale ?: "en"
            val useOffline = settings.ttsMode.value == TtsMode.OFFLINE && speaker.isOfflineAvailable(locale)
            speaker.speak(word.word, if (useOffline) TtsMode.OFFLINE else TtsMode.ONLINE, locale)
        }
    }

    fun choose(option: QuizOption) {
        val s = _state.value
        val target = s.target ?: return
        if (s.answered) return
        registerAnswer(target, option.word.id == target.id) { learned, correctCnt, total ->
            s.copy(answeredId = option.word.id, correctCount = correctCnt, totalCount = total, sessionLearned = learned)
                .let { finalizeIfNeeded(it) }
        }
    }

    fun checkInput(text: String) {
        val s = _state.value
        val target = s.target ?: return
        if (s.answered) return
        val correct = isInputCorrect(text, s.expectedAnswer)
        registerAnswer(target, correct) { learned, correctCnt, total ->
            s.copy(inputAnswered = true, inputCorrect = correct, correctCount = correctCnt, totalCount = total, sessionLearned = learned)
                .let { finalizeIfNeeded(it) }
        }
    }

    /** Общая часть: пишем ответ в БД, считаем «выучено», отдаём новое состояние. */
    private fun registerAnswer(
        target: WordEntity,
        correct: Boolean,
        build: (learned: Int, correctCount: Int, total: Int) -> QuizState
    ) {
        val s = _state.value
        val becameLearned = correct && target.box == Leitner.LEARNED_BOX - 1
        val learned = s.sessionLearned + if (becameLearned) 1 else 0
        _state.value = build(learned, s.correctCount + if (correct) 1 else 0, s.totalCount + 1)
        viewModelScope.launch {
            repo.submitAnswer(target, correct)
            achievements.onAnswer(correct)
        }
    }

    private fun finalizeIfNeeded(state: QuizState): QuizState =
        if (state.totalCount >= SESSION_SIZE) state.copy(finished = true) else state

    fun isInputCorrect(input: String, expected: String): Boolean {
        val user = input.trim().lowercase()
        if (user.isEmpty()) return false
        // Перевод может содержать несколько вариантов — принимаем любой.
        return expected.split(",", ";", "/")
            .map { it.trim().lowercase() }
            .any { it.isNotEmpty() && it == user }
    }

    private fun promptText(w: WordEntity, d: Direction): String =
        if (d == Direction.WORD_TO_TRANSLATION) w.word else w.translation

    private fun answerText(w: WordEntity, d: Direction): String =
        if (d == Direction.WORD_TO_TRANSLATION) w.translation else w.word

    companion object {
        const val SESSION_SIZE = 10
    }
}
