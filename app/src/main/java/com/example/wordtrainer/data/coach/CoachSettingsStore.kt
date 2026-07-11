package com.example.wordtrainer.data.coach

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Настройки ИИ-коуча поверх отдельного SharedPreferences ("coach_settings").
 * Не пересекается с [com.example.wordtrainer.data.SettingsStore] словарной части.
 */
class CoachSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("coach_settings", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        CoachMode.valueOf(prefs.getString(KEY_MODE, CoachMode.CLOUD.name)!!)
    )
    val mode: StateFlow<CoachMode> = _mode.asStateFlow()

    private val _groqApiKey = MutableStateFlow(prefs.getString(KEY_GROQ_KEY, "")!!)
    val groqApiKey: StateFlow<String> = _groqApiKey.asStateFlow()

    private val _serverIp = MutableStateFlow(prefs.getString(KEY_IP, DEFAULT_IP)!!)
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    private val _groqModel = MutableStateFlow(prefs.getString(KEY_GROQ_MODEL, DEFAULT_GROQ_MODEL)!!)
    val groqModel: StateFlow<String> = _groqModel.asStateFlow()

    private val _localModel = MutableStateFlow(prefs.getString(KEY_LOCAL_MODEL, DEFAULT_LOCAL_MODEL)!!)
    val localModel: StateFlow<String> = _localModel.asStateFlow()

    private val _systemPrompt = MutableStateFlow(prefs.getString(KEY_PROMPT, DEFAULT_PROMPT)!!)
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    val isLocal: Boolean get() = _mode.value == CoachMode.LOCAL

    /** Имя активной модели с учётом режима. */
    val currentModel: String get() = if (isLocal) _localModel.value else _groqModel.value

    /** Базовый URL для Retrofit в зависимости от режима. */
    val baseUrl: String
        get() = if (isLocal) "http://${_serverIp.value}:1234/" else "https://api.groq.com/openai/"

    /**
     * Сохраняет всё разом из диалога настроек.
     * [keyOrIp] — API-ключ (облако) или IP сервера (локально).
     */
    fun save(mode: CoachMode, keyOrIp: String, model: String, prompt: String) {
        val e = prefs.edit()
        e.putString(KEY_MODE, mode.name)
        e.putString(KEY_PROMPT, prompt)
        if (mode == CoachMode.LOCAL) {
            e.putString(KEY_IP, keyOrIp)
            e.putString(KEY_LOCAL_MODEL, model)
        } else {
            e.putString(KEY_GROQ_KEY, keyOrIp)
            e.putString(KEY_GROQ_MODEL, model)
        }
        e.apply()

        _mode.value = mode
        _systemPrompt.value = prompt
        if (mode == CoachMode.LOCAL) {
            _serverIp.value = keyOrIp
            _localModel.value = model
        } else {
            _groqApiKey.value = keyOrIp
            _groqModel.value = model
        }
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_GROQ_KEY = "groq_api_key"
        const val KEY_IP = "server_ip"
        const val KEY_GROQ_MODEL = "groq_model"
        const val KEY_LOCAL_MODEL = "local_model"
        const val KEY_PROMPT = "system_prompt"

        const val DEFAULT_IP = "192.168.1.103"
        const val DEFAULT_GROQ_MODEL = "llama-3.3-70b-versatile"
        const val DEFAULT_LOCAL_MODEL = "dolphin-2.9.3-mistral-nemo-12b"
        const val DEFAULT_PROMPT =
            "You are a professional English teacher. Your goal is to help me reach C1 fluency. " +
                "Conduct a natural conversation, but if I make a mistake (grammar, word choice, or spelling), " +
                "stop and correct me politely. Explain the mistake in simple English and provide a correct example."
    }
}
