package com.example.wordtrainer.ui.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wordtrainer.data.AchievementManager
import com.example.wordtrainer.data.SettingsStore
import com.example.wordtrainer.data.WordRepository
import com.example.wordtrainer.data.local.WordEntity
import com.example.wordtrainer.domain.Direction
import com.example.wordtrainer.tts.Speaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FlashState(
    val words: List<WordEntity> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val loading: Boolean = true
) {
    val current: WordEntity? get() = words.getOrNull(index)
    val remaining: Int get() = (words.size - index).coerceAtLeast(0)
    val isEmpty: Boolean get() = !loading && words.isEmpty()
}

class FlashcardsViewModel(
    private val repo: WordRepository,
    private val settings: SettingsStore,
    private val speaker: Speaker,
    private val achievements: AchievementManager
) : ViewModel() {

    private val _state = MutableStateFlow(FlashState())
    val state: StateFlow<FlashState> = _state.asStateFlow()

    val direction: StateFlow<Direction> = settings.direction

    fun load() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val lang = settings.language.value
            repo.seedIfNeeded(lang, settings)
            val batch = repo.nextTrainingBatch(lang)
            _state.value = FlashState(words = batch, index = 0, revealed = false, loading = false)
        }
    }

    fun reveal() {
        if (_state.value.current != null) _state.value = _state.value.copy(revealed = true)
    }

    fun answer(correct: Boolean) {
        val s = _state.value
        val word = s.current ?: return
        viewModelScope.launch {
            repo.submitAnswer(word, correct)
            achievements.onAnswer(correct)
            val nextIndex = s.index + 1
            if (nextIndex >= s.words.size) {
                load() // порция кончилась — берём следующую
            } else {
                _state.value = s.copy(index = nextIndex, revealed = false)
            }
        }
    }

    fun toggleFavorite() {
        val word = _state.value.current ?: return
        viewModelScope.launch {
            repo.toggleFavorite(word)
            val updated = word.copy(isFavorite = !word.isFavorite)
            val list = _state.value.words.toMutableList()
            list[_state.value.index] = updated
            _state.value = _state.value.copy(words = list)
        }
    }

    fun speak() {
        val word = _state.value.current ?: return
        viewModelScope.launch {
            val locale = repo.getLanguage(settings.language.value)?.locale ?: "en"
            speaker.speak(word.word, settings.ttsMode.value, locale)
        }
    }
}
