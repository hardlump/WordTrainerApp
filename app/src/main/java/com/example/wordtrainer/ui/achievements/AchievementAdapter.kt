package com.example.wordtrainer.ui.achievements

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wordtrainer.R
import com.example.wordtrainer.databinding.ItemAchievementBinding
import com.example.wordtrainer.domain.Achievement

data class AchievementRow(val achievement: Achievement, val unlocked: Boolean)

class AchievementAdapter : ListAdapter<AchievementRow, AchievementAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemAchievementBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAchievementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        val ctx = holder.binding.root.context
        with(holder.binding) {
            titleText.text = ctx.getString(row.achievement.titleRes)
            descText.text = ctx.getString(row.achievement.descRes)
            statusText.text = if (row.unlocked) ctx.getString(R.string.achievement_status_unlocked) else ""

            val iconColor = if (row.unlocked) R.color.accent_star else R.color.text_secondary
            iconView.setColorFilter(ContextCompat.getColor(ctx, iconColor))
            val titleColor = if (row.unlocked) R.color.text_primary else R.color.text_secondary
            titleText.setTextColor(ContextCompat.getColor(ctx, titleColor))
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AchievementRow>() {
            override fun areItemsTheSame(a: AchievementRow, b: AchievementRow) =
                a.achievement == b.achievement
            override fun areContentsTheSame(a: AchievementRow, b: AchievementRow) = a == b
        }
    }
}
