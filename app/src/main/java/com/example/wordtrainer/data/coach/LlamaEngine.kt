package com.example.wordtrainer.data.coach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Инференс GGUF-модели прямо на устройстве через llama.cpp (JNI).
 *
 * Нативная часть (`libllamabridge.so`) собирается через NDK/CMake из
 * `app/src/main/cpp` (см. README, раздел «On-device движок»). Пока библиотека не
 * собрана — [available] == false, и [complete] бросает понятную ошибку, а не падает.
 *
 * Модель грузится один раз и переиспользуется, пока не сменится путь к файлу.
 */
class LlamaEngine {

    private val mutex = Mutex()
    private var handle: Long = 0L
    private var loadedPath: String? = null

    suspend fun complete(messages: List<CoachMessage>, modelPath: String?): String =
        withContext(Dispatchers.Default) {
            check(available) { "On-device движок не собран (нужен нативный модуль llama.cpp)" }
            require(!modelPath.isNullOrBlank()) { "Модель GGUF не выбрана" }
            mutex.withLock {
                ensureLoaded(modelPath)
                nativeGenerate(handle, buildPrompt(messages), N_PREDICT).trim()
            }
        }

    private fun ensureLoaded(path: String) {
        if (handle != 0L && path == loadedPath) return
        if (handle != 0L) { nativeFree(handle); handle = 0L }
        handle = nativeLoadModel(path, N_CTX)
        check(handle != 0L) { "Не удалось загрузить модель: $path" }
        loadedPath = path
    }

    /** Простой ChatML-совместимый шаблон; при необходимости уточняется под модель. */
    private fun buildPrompt(messages: List<CoachMessage>): String {
        val sb = StringBuilder()
        for (m in messages) {
            sb.append("<|im_start|>").append(m.role).append('\n')
                .append(m.content).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    fun close() {
        if (handle != 0L) { nativeFree(handle); handle = 0L; loadedPath = null }
    }

    // --- JNI (реализация в app/src/main/cpp/llama_bridge.cpp) ---
    private external fun nativeLoadModel(path: String, nCtx: Int): Long
    private external fun nativeGenerate(handle: Long, prompt: String, nPredict: Int): String
    private external fun nativeFree(handle: Long)

    companion object {
        const val N_CTX = 2048
        const val N_PREDICT = 256

        /** true, если нативная библиотека llama.cpp собрана и загружена. */
        val available: Boolean = runCatching { System.loadLibrary("llamabridge") }.isSuccess
    }
}
