package com.example.wordtrainer.ui

import androidx.fragment.app.Fragment
import com.example.wordtrainer.WordTrainerApp
import com.example.wordtrainer.data.local.WordEntity
import com.example.wordtrainer.domain.Leitner

/** Доступ к ServiceLocator из фрагмента. */
val Fragment.app: WordTrainerApp
    get() = requireActivity().application as WordTrainerApp

/** Индикатор прогресса SRS вида ▮▮▮▯▯ по коробке слова. */
fun WordEntity.boxIndicator(): String {
    val filled = box.coerceIn(0, Leitner.MAX_BOX)
    return "▮".repeat(filled) + "▯".repeat(Leitner.MAX_BOX - filled)
}
