package com.example.wordtrainer.ui.coach

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wordtrainer.R
import com.example.wordtrainer.data.coach.LessonFilter
import com.example.wordtrainer.databinding.FragmentCoachLessonsBinding

/** Список уроков-диалогов: поиск, фильтр со счётчиками, выбор урока. */
class LessonsFragment : Fragment() {

    private var _binding: FragmentCoachLessonsBinding? = null
    private val binding get() = _binding!!

    private val coach get() = requireActivity() as CoachActivity
    private val vm get() = coach.chatViewModel

    private lateinit var adapter: LessonAdapter
    private var query = ""
    private var filter = LessonFilter.ALL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCoachLessonsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = LessonAdapter { lesson -> coach.openLesson(lesson) }
        binding.lessonsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.lessonsRecycler.adapter = adapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                query = s?.toString().orEmpty()
                refresh()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.filterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            filter = when (checkedIds.firstOrNull()) {
                R.id.chipTodo -> LessonFilter.NOT_PASSED
                R.id.chipDone -> LessonFilter.PASSED
                else -> LessonFilter.ALL
            }
            refresh()
        }

        refresh()
    }

    private fun refresh() {
        binding.chipAll.text = getString(R.string.coach_filter_all) + " (${vm.count(LessonFilter.ALL)})"
        binding.chipTodo.text = getString(R.string.coach_filter_todo) + " (${vm.count(LessonFilter.NOT_PASSED)})"
        binding.chipDone.text = getString(R.string.coach_filter_done) + " (${vm.count(LessonFilter.PASSED)})"

        val lessons = vm.filteredLessons(query, filter)
        adapter.submitList(lessons)
        binding.emptyText.visibility = if (lessons.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
