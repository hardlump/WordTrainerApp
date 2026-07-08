package com.example.wordtrainer.ui.wordlist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wordtrainer.data.SettingsStore
import com.example.wordtrainer.data.WordRepository
import com.example.wordtrainer.data.local.WordEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WordListViewModel(
    private val repo: WordRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val favoritesOnly = MutableStateFlow(false)

    val words: StateFlow<List<WordEntity>> =
        combine(settings.language, query, favoritesOnly) { lang, q, favOnly ->
            Triple(lang, q, favOnly)
        }.flatMapLatest { (lang, q, favOnly) ->
            if (favOnly) repo.observeFavorites(lang) else repo.observeWords(lang, q)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(value: String) { query.value = value }
    fun setFavoritesOnly(value: Boolean) { favoritesOnly.value = value }

    fun toggleFavorite(word: WordEntity) = viewModelScope.launch { repo.toggleFavorite(word) }

    fun delete(word: WordEntity) = viewModelScope.launch { repo.deleteWord(word) }

    fun addWord(word: String, translation: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.addWord(word, translation, settings.language.value)
            onResult(ok)
        }
    }

    fun importJson(uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val added = repo.importJson(uri, settings.language.value)
            onResult(added)
        }
    }

    fun exportJson(onResult: (String) -> Unit) {
        viewModelScope.launch { onResult(repo.exportJson(settings.language.value)) }
    }
}
