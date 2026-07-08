package com.example.wordtrainer.ui.wordlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wordtrainer.R
import com.example.wordtrainer.data.local.WordEntity
import com.example.wordtrainer.databinding.ItemWordBinding
import com.example.wordtrainer.ui.boxIndicator

class WordAdapter(
    private val onOpen: (WordEntity) -> Unit,
    private val onStar: (WordEntity) -> Unit,
    private val onLongPress: (WordEntity) -> Unit
) : ListAdapter<WordEntity, WordAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemWordBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemWordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.binding.root.context
        with(holder.binding) {
            wordText.text = item.word
            translationText.text = item.translation
            boxText.text = item.boxIndicator()
            val starColor = if (item.isFavorite) R.color.accent_star else R.color.text_secondary
            starBtn.setColorFilter(ContextCompat.getColor(ctx, starColor))
            starBtn.setOnClickListener { onStar(item) }
            root.setOnClickListener { onOpen(item) }
            root.setOnLongClickListener { onLongPress(item); true }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WordEntity>() {
            override fun areItemsTheSame(a: WordEntity, b: WordEntity) = a.id == b.id
            override fun areContentsTheSame(a: WordEntity, b: WordEntity) = a == b
        }
    }
}
