package com.example.wordtrainer.data.coach

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Настройки ИИ-коуча (отдельный SharedPreferences "coach_settings", без пересечения
 * со словарём). Два режима: модель на телефоне (GGUF) или локальный сервер по сети.
 */
class CoachSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("coach_settings", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<CoachMode> = _mode.asStateFlow()

    // --- Локальный сервер ---
    private val _serverIp = MutableStateFlow(prefs.getString(KEY_IP, DEFAULT_IP)!!)
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    private val _serverModel = MutableStateFlow(prefs.getString(KEY_SERVER_MODEL, DEFAULT_SERVER_MODEL)!!)
    val serverModel: StateFlow<String> = _serverModel.asStateFlow()

    // --- Модель на устройстве (GGUF) ---
    private val _modelUri = MutableStateFlow(prefs.getString(KEY_MODEL_URI, null))
    val modelUri: StateFlow<String?> = _modelUri.asStateFlow()

    private val _modelName = MutableStateFlow(prefs.getString(KEY_MODEL_NAME, "")!!)
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private val _systemPrompt = MutableStateFlow(prefs.getString(KEY_PROMPT, DEFAULT_PROMPT)!!)
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    val isOnDevice: Boolean get() = _mode.value == CoachMode.ON_DEVICE

    /** Базовый URL для серверного режима. */
    val baseUrl: String get() = "http://${_serverIp.value}:1234/"

    /** Сохраняет режим, серверные поля и промпт из диалога. */
    fun save(mode: CoachMode, serverIp: String, serverModel: String, prompt: String) {
        prefs.edit()
            .putString(KEY_MODE, mode.name)
            .putString(KEY_IP, serverIp)
            .putString(KEY_SERVER_MODEL, serverModel)
            .putString(KEY_PROMPT, prompt)
            .apply()
        _mode.value = mode
        _serverIp.value = serverIp
        _serverModel.value = serverModel
        _systemPrompt.value = prompt
    }

    /** Запоминает выбранный пользователем GGUF-файл (persisted URI + имя для показа). */
    fun setImportedModel(uri: String, name: String) {
        prefs.edit().putString(KEY_MODEL_URI, uri).putString(KEY_MODEL_NAME, name).apply()
        _modelUri.value = uri
        _modelName.value = name
    }

    private fun readMode(): CoachMode {
        val saved = prefs.getString(KEY_MODE, CoachMode.LOCAL_SERVER.name)!!
        // Старые значения (CLOUD/LOCAL) и удалённые режимы безопасно откатываем на сервер.
        return runCatching { CoachMode.valueOf(saved) }.getOrDefault(CoachMode.LOCAL_SERVER)
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_IP = "server_ip"
        const val KEY_SERVER_MODEL = "server_model"
        const val KEY_MODEL_URI = "model_uri"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_PROMPT = "system_prompt"

        const val DEFAULT_IP = "192.168.1.103"
        const val DEFAULT_SERVER_MODEL = "local-model"
        const val DEFAULT_PROMPT =
            "You are a professional English teacher. Your goal is to help me reach C1 fluency. " +
                "Conduct a natural conversation, but if I make a mistake (grammar, word choice, or spelling), " +
                "stop and correct me politely. Explain the mistake in simple English and provide a correct example."
    }
}
