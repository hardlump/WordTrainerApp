package com.example.wordtrainer.ui.coach

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wordtrainer.R
import com.example.wordtrainer.data.coach.CoachMessage
import com.example.wordtrainer.databinding.ItemCoachMessageBinding

/** Одно сообщение + признак «сейчас озвучивается» (для смены иконки). */
data class ChatRow(val message: CoachMessage, val isPlaying: Boolean)

class ChatAdapter(
    private val onTranslate: (String) -> Unit,
    private val onSpeakToggle: (ChatRow) -> Unit
) : ListAdapter<ChatRow, ChatAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCoachMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemCoachMessageBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: ChatRow) {
            val ctx = b.root.context
            val isUser = row.message.role == "user"

            b.messageText.text = row.message.content
            b.messageText.maxWidth = (ctx.resources.displayMetrics.widthPixels * 0.78).toInt()

            val bubbleColor = if (isUser) R.color.brand_primary else R.color.card_bg
            val textColor = if (isUser) R.color.white else R.color.text_primary
            val iconTint = if (isUser) R.color.white else R.color.text_secondary
            b.bubble.setCardBackgroundColor(ContextCompat.getColor(ctx, bubbleColor))
            b.messageText.setTextColor(ContextCompat.getColor(ctx, textColor))
            b.translateBtn.setColorFilter(ContextCompat.getColor(ctx, iconTint))

            (b.bubble.layoutParams as FrameLayout.LayoutParams).gravity =
                if (isUser) android.view.Gravity.END else android.view.Gravity.START

            if (row.isPlaying) {
                b.speakBtn.setImageResource(R.drawable.ic_stop)
                b.speakBtn.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_wrong))
            } else {
                b.speakBtn.setImageResource(R.drawable.ic_speak)
                b.speakBtn.setColorFilter(ContextCompat.getColor(ctx, iconTint))
            }

            b.translateBtn.setOnClickListener { onTranslate(row.message.content) }
            b.speakBtn.setOnClickListener { onSpeakToggle(row) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ChatRow>() {
            override fun areItemsTheSame(a: ChatRow, b: ChatRow) = a.message === b.message
            override fun areContentsTheSame(a: ChatRow, b: ChatRow) = a == b
        }
    }
}
