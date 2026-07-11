package com.example.wordtrainer.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wordtrainer.data.coach.CoachRepository
import com.example.wordtrainer.data.coach.SentenceExercise
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SmartState(
    val exercise: SentenceExercise? = null,
    val selection: List<Int> = emptyList(),     // индексы плиток в exercise.options
    val isLoading: Boolean = false,
    val isChecked: Boolean = false,
    val isCorrect: Boolean = false,
    val wordCorrect: List<Boolean> = emptyList() // корректность по позициям selection
) {
    val usedOptions: Set<Int> get() = selection.toSet()
}

/**
 * Упражнение «собери предложение из слов»: плитки со словами добавляются в ответ
 * тапом (tap-to-assemble). Предложения генерирует ИИ по случайной теме.
 */
class CoachSmartViewModel(private val repo: CoachRepository) : ViewModel() {

    private val _state = MutableStateFlow(SmartState())
    val state: StateFlow<SmartState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    fun loadNext() {
        _state.value = SmartState(isLoading = true)
        viewModelScope.launch {
            try {
                val ex = repo.generateExercise()
                if (ex != null) {
                    _state.value = SmartState(exercise = ex)
                } else {
                    _state.value = SmartState()
                    _errors.tryEmit("parse")
                }
            } catch (e: Exception) {
                _state.value = SmartState()
                _errors.tryEmit(e.localizedMessage ?: "network error")
            }
        }
    }

    fun selectWord(optionIndex: Int) {
        val s = _state.value
        if (s.isChecked || optionIndex in s.selection) return
        _state.value = s.copy(selection = s.selection + optionIndex)
    }

    fun removeAt(position: Int) {
        val s = _state.value
        if (s.isChecked || position !in s.selection.indices) return
        _state.value = s.copy(selection = s.selection.toMutableList().apply { removeAt(position) })
    }

    fun giveHint() {
        val s = _state.value
        val exercise = s.exercise ?: return
        if (s.isChecked) return
        val correctWords = correctWords(exercise)
        val newSelection = mutableListOf<Int>()
        val used = mutableSetOf<Int>()
        for (i in correctWords.indices) {
            val target = correctWords[i]
            val current = s.selection.getOrNull(i)
            if (current != null && exercise.options[current] == target) {
                newSelection.add(current)
                used.add(current)
            } else {
                val needed = exercise.options.indices.firstOrNull {
                    exercise.options[it] == target && it !in used
                }
                if (needed != null) newSelection.add(needed)
                break // одна подсказка за раз
            }
        }
        _state.value = s.copy(selection = newSelection)
    }

    fun check() {
        val s = _state.value
        val exercise = s.exercise ?: return
        val correctWords = correctWords(exercise)
        val statuses = s.selection.mapIndexed { index, optIndex ->
            correctWords.getOrNull(index) == exercise.options[optIndex]
        }
        val correct = statuses.all { it } && s.selection.size == correctWords.size
        _state.value = s.copy(isChecked = true, isCorrect = correct, wordCorrect = statuses)
    }

    private fun correctWords(exercise: SentenceExercise): List<String> =
        exercise.correctAnswer.replace(Regex("[.!?,]"), "").trim().split(" ").filter { it.isNotEmpty() }
}
