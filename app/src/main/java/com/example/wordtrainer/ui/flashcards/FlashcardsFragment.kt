package com.example.wordtrainer.ui.flashcards

import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wordtrainer.R
import com.example.wordtrainer.databinding.FragmentFlashcardsBinding
import com.example.wordtrainer.domain.Direction
import com.example.wordtrainer.ui.app
import kotlinx.coroutines.launch
import kotlin.math.abs

class FlashcardsFragment : Fragment() {

    private var _binding: FragmentFlashcardsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FlashcardsViewModel by viewModels {
        viewModelFactory {
            initializer { FlashcardsViewModel(app.repository, app.settings, app.speaker) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentFlashcardsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupCardGestures()

        binding.knowBtn.setOnClickListener { viewModel.answer(correct = true) }
        binding.dontKnowBtn.setOnClickListener { viewModel.answer(correct = false) }
        binding.speakBtn.setOnClickListener { viewModel.speak() }
        binding.favoriteBtn.setOnClickListener { viewModel.toggleFavorite() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                // Смена языка/направления — перезагружаем порцию.
                launch { app.settings.language.collect { viewModel.load() } }
                launch { app.settings.direction.collect { render(viewModel.state.value) } }
            }
        }
    }

    private fun setupCardGestures() {
        val detector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                viewModel.reveal()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                if (abs(dx) > 120 && abs(dx) > abs(dy)) {
                    viewModel.answer(correct = dx > 0) // вправо — знаю, влево — не знаю
                    return true
                }
                return false
            }
        })
        binding.card.isClickable = true
        binding.card.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            detector.onTouchEvent(event)
        }
    }

    private fun render(state: FlashState) {
        binding.emptyText.visibility = if (state.isEmpty) View.VISIBLE else View.GONE
        binding.card.visibility = if (state.isEmpty) View.INVISIBLE else View.VISIBLE

        val word = state.current
        if (word == null) {
            binding.promptText.text = ""
            binding.answerText.text = ""
            binding.remainingText.text = ""
            return
        }

        val forward = app.settings.direction.value == Direction.WORD_TO_TRANSLATION
        binding.promptText.text = if (forward) word.word else word.translation
        binding.answerText.text = if (forward) word.translation else word.word
        binding.answerText.visibility = if (state.revealed) View.VISIBLE else View.INVISIBLE
        binding.hintText.visibility = if (state.revealed) View.INVISIBLE else View.VISIBLE
        binding.remainingText.text = getString(R.string.remaining, state.remaining)

        val starColor = if (word.isFavorite) R.color.accent_star else R.color.text_secondary
        binding.favoriteBtn.setIconTint(
            android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), starColor))
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
