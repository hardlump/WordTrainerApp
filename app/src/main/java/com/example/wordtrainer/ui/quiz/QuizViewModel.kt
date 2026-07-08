package com.example.wordtrainer.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wordtrainer.data.SettingsStore
import com.example.wordtrainer.data.WordRepository
import com.example.wordtrainer.data.local.WordEntity
import com.example.wordtrainer.domain.Direction
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
    val answeredId: Long? = null,   // id выбранного варианта после ответа
    val correctId: Long? = null,    // id правильного варианта
    val correctCount: Int = 0,
    val totalCount: Int = 0,
    val loading: Boolean = true,
    val notEnough: Boolean = false  // слишком мало слов для квиза
)

class QuizViewModel(
    private val repo: WordRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state.asStateFlow()

    fun start() {
        _state.value = QuizState(loading = true)
        nextQuestion(resetScore = true)
    }

    fun nextQuestion(resetScore: Boolean = false) {
        viewModelScope.launch {
            val lang = settings.language.value
            repo.seedIfNeeded(lang, settings)
            val batch = repo.nextTrainingBatch(lang, limit = 1)
            val target = batch.firstOrNull()
            if (target == null) {
                _state.value = _state.value.copy(loading = false, notEnough = true, target = null)
                return@launch
            }
            val distractors = repo.distractorsFor(target, count = 3)
            if (distractors.size < 1) {
                _state.value = _state.value.copy(loading = false, notEnough = true, target = null)
                return@launch
            }
            val direction = settings.direction.value
            val options = (distractors + target).map { QuizOption(it, answerText(it, direction)) }.shuffled()
            val prev = _state.value
            _state.value = QuizState(
                target = target,
                prompt = promptText(target, direction),
                options = options,
                direction = direction,
                correctId = target.id,
                correctCount = if (resetScore) 0 else prev.correctCount,
                totalCount = if (resetScore) 0 else prev.totalCount,
                loading = false
            )
        }
    }

    fun choose(option: QuizOption) {
        val s = _state.value
        val target = s.target ?: return
        if (s.answeredId != null) return // уже ответили
        val correct = option.word.id == target.id
        viewModelScope.launch { repo.submitAnswer(target, correct) }
        _state.value = s.copy(
            answeredId = option.word.id,
            correctCount = s.correctCount + if (correct) 1 else 0,
            totalCount = s.totalCount + 1
        )
    }

    private fun promptText(w: WordEntity, d: Direction): String =
        if (d == Direction.WORD_TO_TRANSLATION) w.word else w.translation

    private fun answerText(w: WordEntity, d: Direction): String =
        if (d == Direction.WORD_TO_TRANSLATION) w.translation else w.word
}
