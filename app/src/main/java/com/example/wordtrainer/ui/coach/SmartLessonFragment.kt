package com.example.wordtrainer.ui.coach

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wordtrainer.R
import com.example.wordtrainer.WordTrainerApp
import com.example.wordtrainer.databinding.FragmentCoachSmartBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

/** Упражнение «собери предложение из слов» (tap-to-assemble). */
class SmartLessonFragment : Fragment() {

    private var _binding: FragmentCoachSmartBinding? = null
    private val binding get() = _binding!!

    private val vm: CoachSmartViewModel by viewModels {
        val app = requireActivity().application as WordTrainerApp
        viewModelFactory { initializer { CoachSmartViewModel(app.coachRepository) } }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCoachSmartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.hintBtn.setOnClickListener { vm.giveHint() }
        binding.actionBtn.setOnClickListener {
            if (vm.state.value.isChecked) vm.loadNext() else vm.check()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect { render(it) } }
                launch {
                    vm.errors.collect {
                        Toast.makeText(requireContext(), getString(R.string.coach_error, it), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        if (vm.state.value.exercise == null && !vm.state.value.isLoading) vm.loadNext()
    }

    private fun render(state: SmartState) {
        binding.loading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.contentPanel.visibility = if (state.isLoading) View.INVISIBLE else View.VISIBLE
        val exercise = state.exercise ?: return

        binding.questionText.text = exercise.question
        binding.hintBtn.visibility = if (state.isChecked) View.GONE else View.VISIBLE

        buildAnswerChips(state)
        buildBankChips(state)

        if (state.isChecked) {
            binding.resultText.visibility = View.VISIBLE
            if (state.isCorrect) {
                binding.resultText.text = getString(R.string.coach_correct)
                binding.resultText.setTextColor(color(R.color.accent_correct))
            } else {
                binding.resultText.text = getString(R.string.coach_wrong, exercise.correctAnswer)
                binding.resultText.setTextColor(color(R.color.accent_wrong))
            }
            binding.actionBtn.setText(R.string.coach_next)
            binding.actionBtn.isEnabled = true
        } else {
            binding.resultText.visibility = View.GONE
            binding.actionBtn.setText(R.string.coach_check)
            binding.actionBtn.isEnabled = state.selection.isNotEmpty()
        }
    }

    private fun buildAnswerChips(state: SmartState) {
        val exercise = state.exercise ?: return
        binding.answerGroup.removeAllViews()
        state.selection.forEachIndexed { position, optIndex ->
            val chip = newChip(exercise.options[optIndex])
            if (state.isChecked) {
                val correct = state.wordCorrect.getOrNull(position) ?: false
                paintChip(chip, if (correct) R.color.accent_correct else R.color.accent_wrong, R.color.white)
            } else {
                paintChip(chip, R.color.brand_primary, R.color.white)
                chip.setOnClickListener { vm.removeAt(position) }
            }
            binding.answerGroup.addView(chip)
        }
    }

    private fun buildBankChips(state: SmartState) {
        val exercise = state.exercise ?: return
        binding.bankGroup.removeAllViews()
        exercise.options.forEachIndexed { index, word ->
            if (index in state.usedOptions) return@forEachIndexed
            val chip = newChip(word)
            paintChip(chip, R.color.card_bg, R.color.text_primary)
            chip.isEnabled = !state.isChecked
            if (!state.isChecked) chip.setOnClickListener { vm.selectWord(index) }
            binding.bankGroup.addView(chip)
        }
    }

    private fun newChip(text: String): Chip = Chip(requireContext()).apply {
        this.text = text
        isCheckable = false
        isClickable = true
        chipStrokeWidth = 1f
        chipStrokeColor = ColorStateList.valueOf(color(R.color.text_secondary))
    }

    private fun paintChip(chip: Chip, bg: Int, text: Int) {
        chip.chipBackgroundColor = ColorStateList.valueOf(color(bg))
        chip.setTextColor(color(text))
    }

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
