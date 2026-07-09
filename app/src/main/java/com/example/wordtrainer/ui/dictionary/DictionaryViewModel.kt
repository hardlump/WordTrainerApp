package com.example.wordtrainer.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wordtrainer.data.SettingsStore
import com.example.wordtrainer.data.WordRepository
import com.example.wordtrainer.data.local.DictionaryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DictionaryUiState(
    val loading: Boolean = true,
    val results: List<DictionaryEntry> = emptyList()
)

class DictionaryViewModel(
    private val repo: WordRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(DictionaryUiState())
    val state: StateFlow<DictionaryUiState> = _state.asStateFlow()

    private var query = ""

    /** Первый вход: досеиваем словарь (если нужно) и показываем начало. */
    fun start() {
        viewModelScope.launch {
            repo.seedDictionaryIfNeeded()
            _state.value = DictionaryUiState(loading = false, results = repo.searchDictionary(query))
        }
    }

    fun search(q: String) {
        query = q
        viewModelScope.launch {
            _state.value = _state.value.copy(results = repo.searchDictionary(q))
        }
    }

    fun add(entry: DictionaryEntry, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(repo.addFromDictionary(entry, settings.language.value))
        }
    }
}
