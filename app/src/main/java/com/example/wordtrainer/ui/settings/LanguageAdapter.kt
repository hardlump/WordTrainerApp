package com.example.wordtrainer.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wordtrainer.data.local.LanguageEntity
import com.example.wordtrainer.databinding.ItemLanguageBinding

data class LanguageRow(val lang: LanguageEntity, val isCurrent: Boolean)

class LanguageAdapter(
    private val onSelect: (LanguageEntity) -> Unit,
    private val onDelete: (LanguageEntity) -> Unit
) : ListAdapter<LanguageRow, LanguageAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemLanguageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        with(holder.binding) {
            nameText.text = row.lang.name
            localeText.text = row.lang.locale
            currentIcon.visibility = if (row.isCurrent) View.VISIBLE else View.INVISIBLE
            root.setOnClickListener { onSelect(row.lang) }
            deleteBtn.setOnClickListener { onDelete(row.lang) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<LanguageRow>() {
            override fun areItemsTheSame(a: LanguageRow, b: LanguageRow) = a.lang.code == b.lang.code
            override fun areContentsTheSame(a: LanguageRow, b: LanguageRow) = a == b
        }
    }
}
