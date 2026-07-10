package com.example.wordtrainer.ui.wordlist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wordtrainer.data.SettingsStore
import com.example.wordtrainer.data.WordRepository
import com.example.wordtrainer.data.local.WordEntity
import com.example.wordtrainer.domain.Leitner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Фильтр по статусу слова. */
enum class WordStatus { ALL, NEW, LEARNING, LEARNED }

/** Порядок сортировки списка. */
enum class WordSort { ALPHABETICAL, NEWEST, PROGRESS, HARDEST }

@OptIn(ExperimentalCoroutinesApi::class)
class WordListViewModel(
    private val repo: WordRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val favoritesOnly = MutableStateFlow(false)
    private val statusFilter = MutableStateFlow(WordStatus.ALL)
    private val sortOrder = MutableStateFlow(WordSort.ALPHABETICAL)

    val status: StateFlow<WordStatus> = statusFilter.asStateFlow()
    val sort: StateFlow<WordSort> = sortOrder.asStateFlow()

    private data class Params(
        val lang: String,
        val query: String,
        val favoritesOnly: Boolean,
        val status: WordStatus,
        val sort: WordSort
    )

    val words: StateFlow<List<WordEntity>> =
        combine(settings.language, query, favoritesOnly, statusFilter, sortOrder) { lang, q, fav, st, so ->
            Params(lang, q, fav, st, so)
        }.flatMapLatest { p ->
            val base = if (p.favoritesOnly) repo.observeFavorites(p.lang) else repo.observeWords(p.lang, p.query)
            base.map { list -> applyFilterSort(list, p.status, p.sort) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(value: String) { query.value = value }
    fun setFavoritesOnly(value: Boolean) { favoritesOnly.value = value }
    fun setStatus(value: WordStatus) { statusFilter.value = value }
    fun setSort(value: WordSort) { sortOrder.value = value }

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

    private fun applyFilterSort(list: List<WordEntity>, status: WordStatus, sort: WordSort): List<WordEntity> {
        val filtered = if (status == WordStatus.ALL) list else list.filter { matchesStatus(it, status) }
        return filtered.sortedWith(comparator(sort))
    }

    private fun matchesStatus(w: WordEntity, status: WordStatus): Boolean = when (status) {
        WordStatus.ALL -> true
        WordStatus.NEW -> w.box == 1 && w.lastReviewedAt == null
        WordStatus.LEARNED -> w.box >= Leitner.LEARNED_BOX
        WordStatus.LEARNING -> w.box < Leitner.LEARNED_BOX && !(w.box == 1 && w.lastReviewedAt == null)
    }

    private fun comparator(sort: WordSort): Comparator<WordEntity> = when (sort) {
        WordSort.ALPHABETICAL -> compareBy { it.word.lowercase() }
        WordSort.NEWEST -> compareByDescending { it.createdAt }
        WordSort.PROGRESS -> compareByDescending<WordEntity> { it.box }.thenBy { it.word.lowercase() }
        WordSort.HARDEST -> compareByDescending<WordEntity> { it.wrongCount }.thenBy { it.word.lowercase() }
    }
}
