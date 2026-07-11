package com.example.wordtrainer.ui.coach

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.wordtrainer.R
import com.example.wordtrainer.WordTrainerApp
import com.example.wordtrainer.data.coach.CoachMode
import com.example.wordtrainer.databinding.DialogCoachSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Настройки ИИ: режим (модель на устройстве / локальный сервер), импорт GGUF, промпт. */
class CoachSettingsDialog : DialogFragment() {

    private var _binding: DialogCoachSettingsBinding? = null
    private val binding get() = _binding!!

    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importModel(uri)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val settings = (requireActivity().application as WordTrainerApp).coachSettings
        _binding = DialogCoachSettingsBinding.inflate(layoutInflater)

        binding.onDeviceSwitch.isChecked = settings.isOnDevice
        binding.ipInput.setText(settings.serverIp.value)
        binding.serverModelInput.setText(settings.serverModel.value)
        binding.promptInput.setText(settings.systemPrompt.value)
        showModelName(settings.modelName.value)
        applyMode(settings.isOnDevice)

        binding.onDeviceSwitch.setOnCheckedChangeListener { _, checked -> applyMode(checked) }
        binding.importModelBtn.setOnClickListener { pickModel.launch(arrayOf("*/*")) }
        binding.clearHistoryBtn.setOnClickListener {
            (requireActivity() as CoachActivity).chatViewModel.clearHistory()
            Toast.makeText(requireContext(), R.string.coach_clear_history, Toast.LENGTH_SHORT).show()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.coach_settings_title)
            .setView(binding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                settings.save(
                    mode = if (binding.onDeviceSwitch.isChecked) CoachMode.ON_DEVICE else CoachMode.LOCAL_SERVER,
                    serverIp = binding.ipInput.text?.toString().orEmpty().trim(),
                    serverModel = binding.serverModelInput.text?.toString().orEmpty().trim(),
                    prompt = binding.promptInput.text?.toString().orEmpty().trim()
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun applyMode(onDevice: Boolean) {
        binding.onDeviceSection.visibility = if (onDevice) View.VISIBLE else View.GONE
        binding.serverSection.visibility = if (onDevice) View.GONE else View.VISIBLE
    }

    private fun showModelName(name: String) {
        binding.modelNameText.text =
            if (name.isBlank()) getString(R.string.coach_model_none) else name
    }

    /** Копирует выбранный GGUF в каталог приложения (llama.cpp нужен путь в ФС). */
    private fun importModel(uri: Uri) {
        val app = requireActivity().application as WordTrainerApp
        val ctx = requireContext().applicationContext
        val name = queryName(uri) ?: "model.gguf"
        Toast.makeText(ctx, R.string.coach_importing, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = File(ctx.filesDir, "coach_model.gguf")
                    ctx.contentResolver.openInputStream(uri)!!.use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    }
                    dest.absolutePath
                }.getOrNull()
            }
            if (path != null) {
                app.coachSettings.setImportedModel(path, name)
                showModelName(name)
                Toast.makeText(ctx, R.string.coach_import_done, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, R.string.coach_import_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun queryName(uri: Uri): String? =
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
