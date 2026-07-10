package com.example.wordtrainer.ui.quiz

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
import com.example.wordtrainer.domain.QuizMode
import com.example.wordtrainer.ui.app
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class QuizFragment : Fragment() {

    private var _binding: FragmentQuizBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QuizViewModel by viewModels {
        viewModelFactory {
            initializer { QuizViewModel(app.repository, app.settings, app.achievements, app.speaker) }
        }
    }

    private lateinit var optionButtons: List<MaterialButton>
    private var lastTargetId: Long? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentQuizBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        optionButtons = listOf(binding.option0, binding.option1, binding.option2, binding.option3)

        binding.nextBtn.setOnClickListener { viewModel.nextQuestion() }
        binding.restartBtn.setOnClickListener { viewModel.start() }
        binding.checkBtn.setOnClickListener { submitInput() }
        binding.answerInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submitInput(); true } else false
        }
        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.setMode(
                when (checkedId) {
                    R.id.modeChoiceBtn -> QuizMode.CHOICE
                    R.id.modeInputBtn -> QuizMode.INPUT
                    else -> QuizMode.LISTENING
                }
            )
        }
        binding.replayBtn.setOnClickListener { viewModel.replay() }
        binding.revealWordBtn.setOnClickListener {
            binding.revealedWordText.text = viewModel.state.value.prompt
            binding.revealedWordText.visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch { app.settings.language.collect { viewModel.start() } }
                launch { app.settings.direction.collect { viewModel.start() } }
            }
        }
    }

    private fun submitInput() {
        if (viewModel.state.value.inputAnswered) return
        viewModel.checkInput(binding.answerInput.text?.toString().orEmpty())
    }

    private fun render(state: QuizState) {
        // Переключатель режима держим в синхроне с состоянием без лишних событий.
        val desiredMode = when (state.mode) {
            QuizMode.CHOICE -> R.id.modeChoiceBtn
            QuizMode.INPUT -> R.id.modeInputBtn
            QuizMode.LISTENING -> R.id.modeListenBtn
        }
        if (binding.modeToggle.checkedButtonId != desiredMode) binding.modeToggle.check(desiredMode)

        binding.scoreText.text = getString(R.string.quiz_score, state.correctCount, state.totalCount)

        // Экран «мало слов».
        binding.emptyText.visibility = if (state.notEnough) View.VISIBLE else View.GONE

        // Итог сессии.
        if (state.finished) {
            binding.summaryPanel.visibility = View.VISIBLE
            binding.summaryStats.text = getString(
                R.string.quiz_summary,
                state.correctCount,
                state.totalCount,
                if (state.totalCount == 0) 0 else state.correctCount * 100 / state.totalCount,
                state.sessionLearned
            )
        } else {
            binding.summaryPanel.visibility = View.GONE
        }

        val hasQuestion = state.target != null && !state.notEnough && !state.finished
        val choiceMode = state.mode == QuizMode.CHOICE
        val listening = state.mode == QuizMode.LISTENING

        // В режиме «на слух» слово не показываем — только озвучка.
        binding.promptText.visibility = if (hasQuestion && !listening) View.VISIBLE else View.INVISIBLE
        binding.promptText.text = if (listening) "" else state.prompt
        binding.listenContainer.visibility = if (hasQuestion && listening) View.VISIBLE else View.GONE
        binding.optionsContainer.visibility = if (hasQuestion && choiceMode) View.VISIBLE else View.GONE
        // Ввод и слух отвечают через одно и то же поле.
        binding.inputContainer.visibility = if (hasQuestion && !choiceMode) View.VISIBLE else View.GONE

        // Новый вопрос — прячем показанное слово (фолбэк для «на слух»).
        if (state.target?.id != lastTargetId) {
            lastTargetId = state.target?.id
            binding.revealedWordText.visibility = View.GONE
        }

        if (choiceMode) renderChoice(state) else renderInput(state)

        binding.nextBtn.visibility = if (hasQuestion && state.answered) View.VISIBLE else View.INVISIBLE
    }

    private fun renderChoice(state: QuizState) {
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
    }

    private fun renderInput(state: QuizState) {
        binding.answerInput.isEnabled = !state.inputAnswered
        binding.checkBtn.isEnabled = !state.inputAnswered
        if (!state.inputAnswered) {
            binding.answerInput.text?.clear()
            binding.feedbackText.visibility = View.GONE
        } else {
            binding.feedbackText.visibility = View.VISIBLE
            if (state.inputCorrect) {
                binding.feedbackText.text = getString(R.string.quiz_correct)
                binding.feedbackText.setTextColor(color(R.color.accent_correct))
            } else {
                binding.feedbackText.text = getString(R.string.quiz_wrong, state.expectedAnswer)
                binding.feedbackText.setTextColor(color(R.color.accent_wrong))
            }
        }
    }

    /** Красит варианты после ответа: правильный — зелёный, ошибочный выбор — красный. */
    private fun styleOption(button: MaterialButton, state: QuizState, optionId: Long) {
        when {
            state.answeredId == null -> setOption(button, R.color.card_bg, R.color.text_primary)
            optionId == state.correctId -> setOption(button, R.color.accent_correct, R.color.white)
            optionId == state.answeredId -> setOption(button, R.color.accent_wrong, R.color.white)
            else -> setOption(button, R.color.card_bg, R.color.text_primary)
        }
    }

    private fun setOption(button: MaterialButton, bg: Int, text: Int) {
        button.backgroundTintList = ColorStateList.valueOf(color(bg))
        button.setTextColor(color(text))
    }

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
