package com.example.wordtrainer.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wordtrainer.data.SettingsStore
import com.example.wordtrainer.data.WordRepository
import com.example.wordtrainer.data.local.WordEntity
import com.example.wordtrainer.tts.Speaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WordDetailViewModel(
    private val repo: WordRepository,
    private val settings: SettingsStore,
    private val speaker: Speaker
) : ViewModel() {

    private val _word = MutableStateFlow<WordEntity?>(null)
    val word: StateFlow<WordEntity?> = _word.asStateFlow()

    private var wordId: Long = 0

    fun load(id: Long) {
        wordId = id
        reload()
    }

    private fun reload() {
        viewModelScope.launch { _word.value = repo.getWord(wordId) }
    }

    fun toggleFavorite() {
        val current = _word.value ?: return
        viewModelScope.launch { repo.toggleFavorite(current); reload() }
    }

    fun resetProgress() {
        val current = _word.value ?: return
        viewModelScope.launch { repo.resetProgress(current); reload() }
    }

    fun markLearned() {
        val current = _word.value ?: return
        viewModelScope.launch { repo.markLearned(current); reload() }
    }

    fun edit(newWord: String, newTranslation: String, onResult: (Boolean) -> Unit) {
        val current = _word.value ?: return
        viewModelScope.launch {
            val ok = repo.updateWordText(current, newWord, newTranslation)
            if (ok) reload()
            onResult(ok)
        }
    }

    fun delete(onDone: () -> Unit) {
        val current = _word.value ?: return
        viewModelScope.launch { repo.deleteWord(current); onDone() }
    }

    fun speak() {
        val current = _word.value ?: return
        viewModelScope.launch {
            val locale = repo.getLanguage(current.lang)?.locale ?: "en"
            speaker.speak(current.word, settings.ttsMode.value, locale)
        }
    }
}
