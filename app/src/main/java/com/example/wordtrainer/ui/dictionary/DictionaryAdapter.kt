package com.example.wordtrainer.ui.dictionary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wordtrainer.data.local.DictionaryEntry
import com.example.wordtrainer.databinding.ItemDictionaryBinding

class DictionaryAdapter(
    private val onAdd: (DictionaryEntry) -> Unit
) : ListAdapter<DictionaryEntry, DictionaryAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemDictionaryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDictionaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            wordText.text = item.word
            translationText.text = item.translation
            val meta = listOfNotNull(item.transcription, item.partOfSpeech)
                .filter { it.isNotBlank() }.joinToString(" · ")
            metaText.text = meta
            metaText.visibility = if (meta.isBlank()) View.GONE else View.VISIBLE
            addBtn.setOnClickListener { onAdd(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DictionaryEntry>() {
            override fun areItemsTheSame(a: DictionaryEntry, b: DictionaryEntry) = a.id == b.id
            override fun areContentsTheSame(a: DictionaryEntry, b: DictionaryEntry) = a == b
        }
    }
}
