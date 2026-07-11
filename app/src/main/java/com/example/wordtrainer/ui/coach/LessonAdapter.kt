package com.example.wordtrainer.ui.coach

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wordtrainer.data.coach.Lesson
import com.example.wordtrainer.databinding.ItemCoachLessonBinding

class LessonAdapter(
    private val onClick: (Lesson) -> Unit
) : ListAdapter<Lesson, LessonAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCoachLessonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemCoachLessonBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(lesson: Lesson) {
            b.lessonTitle.text = lesson.title
            b.lessonDesc.text = lesson.description
            b.passedIcon.visibility = if (lesson.isPassed) View.VISIBLE else View.GONE
            b.root.setOnClickListener { onClick(lesson) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Lesson>() {
            override fun areItemsTheSame(a: Lesson, b: Lesson) = a.title == b.title
            override fun areContentsTheSame(a: Lesson, b: Lesson) = a == b
        }
    }
}
