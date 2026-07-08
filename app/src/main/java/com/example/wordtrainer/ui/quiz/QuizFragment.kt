package com.example.wordtrainer.ui.quiz

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
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
import com.example.wordtrainer.databinding.FragmentQuizBinding
import com.example.wordtrainer.ui.app
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class QuizFragment : Fragment() {

    private var _binding: FragmentQuizBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QuizViewModel by viewModels {
        viewModelFactory {
            initializer { QuizViewModel(app.repository, app.settings) }
        }
    }

    private lateinit var optionButtons: List<MaterialButton>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentQuizBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        optionButtons = listOf(binding.option0, binding.option1, binding.option2, binding.option3)
        binding.nextBtn.setOnClickListener { viewModel.nextQuestion() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch { app.settings.language.collect { viewModel.start() } }
                launch { app.settings.direction.collect { viewModel.start() } }
            }
        }
    }

    private fun render(state: QuizState) {
        binding.emptyText.visibility = if (state.notEnough) View.VISIBLE else View.GONE
        val hasQuestion = state.target != null && !state.notEnough
        binding.promptText.visibility = if (hasQuestion) View.VISIBLE else View.INVISIBLE
        binding.optionsContainer.visibility = if (hasQuestion) View.VISIBLE else View.INVISIBLE

        binding.scoreText.text = getString(R.string.quiz_score, state.correctCount, state.totalCount)
        binding.promptText.text = state.prompt

        val answered = state.answeredId != null
        optionButtons.forEachIndexed { i, button ->
            val option = state.options.getOrNull(i)
            if (option == null) {
                button.visibility = View.GONE
                return@forEachIndexed
            }
            button.visibility = View.VISIBLE
            button.text = option.text
            button.isEnabled = !answered
            button.setOnClickListener { viewModel.choose(option) }
            styleOption(button, state, option.word.id)
        }

        binding.nextBtn.visibility = if (answered) View.VISIBLE else View.INVISIBLE
    }

    /** Красит варианты после ответа: правильный — зелёный, ошибочный выбор — красный. */
    private fun styleOption(button: MaterialButton, state: QuizState, optionId: Long) {
        val ctx = requireContext()
        val neutralText = ContextCompat.getColor(ctx, R.color.text_primary)
        val white = ContextCompat.getColor(ctx, R.color.white)
        when {
            state.answeredId == null -> {
                button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.card_bg))
                button.setTextColor(neutralText)
            }
            optionId == state.correctId -> {
                button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.accent_correct))
                button.setTextColor(white)
            }
            optionId == state.answeredId -> {
                button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.accent_wrong))
                button.setTextColor(white)
            }
            else -> {
                button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.card_bg))
                button.setTextColor(neutralText)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
