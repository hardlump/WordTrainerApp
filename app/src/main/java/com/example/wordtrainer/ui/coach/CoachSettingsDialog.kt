package com.example.wordtrainer.ui.coach

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.wordtrainer.R
import com.example.wordtrainer.WordTrainerApp
import com.example.wordtrainer.data.coach.CoachMode
import com.example.wordtrainer.databinding.DialogCoachSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Настройки ИИ: режим (облако/локально), ключ или IP, модель и промпт-персона. */
class CoachSettingsDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val app = requireActivity().application as WordTrainerApp
        val settings = app.coachSettings
        val binding = DialogCoachSettingsBinding.inflate(layoutInflater)

        fun applyMode(local: Boolean) {
            binding.keyOrIpInput.setHint(if (local) R.string.coach_field_ip else R.string.coach_field_key)
            binding.keyOrIpInput.setText(if (local) settings.serverIp.value else settings.groqApiKey.value)
            binding.modelInput.setText(if (local) settings.localModel.value else settings.groqModel.value)
        }

        binding.localSwitch.isChecked = settings.isLocal
        binding.promptInput.setText(settings.systemPrompt.value)
        applyMode(settings.isLocal)

        binding.localSwitch.setOnCheckedChangeListener { _, checked -> applyMode(checked) }

        binding.clearHistoryBtn.setOnClickListener {
            (requireActivity() as CoachActivity).chatViewModel.clearHistory()
            Toast.makeText(requireContext(), R.string.coach_clear_history, Toast.LENGTH_SHORT).show()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.coach_settings_title)
            .setView(binding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val mode = if (binding.localSwitch.isChecked) CoachMode.LOCAL else CoachMode.CLOUD
                settings.save(
                    mode = mode,
                    keyOrIp = binding.keyOrIpInput.text?.toString().orEmpty().trim(),
                    model = binding.modelInput.text?.toString().orEmpty().trim(),
                    prompt = binding.promptInput.text?.toString().orEmpty().trim()
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }
}
