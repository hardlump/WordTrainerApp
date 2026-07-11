package com.example.wordtrainer.data.coach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

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
    private var loadedStamp: Long = 0L

    suspend fun complete(messages: List<CoachMessage>, modelPath: String?): String =
        withContext(Dispatchers.Default) {
            check(available) { "On-device движок не собран (нужен нативный модуль llama.cpp)" }
            require(!modelPath.isNullOrBlank()) { "Модель GGUF не выбрана" }
            mutex.withLock {
                ensureLoaded(modelPath)
                // Шаблон чата применяет нативная часть (родной шаблон модели из GGUF).
                val roles = Array(messages.size) { messages[it].role }
                val contents = Array(messages.size) { messages[it].content }
                nativeGenerate(handle, roles, contents, N_PREDICT).trim()
            }
        }

    private fun ensureLoaded(path: String) {
        // Импорт перезаписывает файл под тем же именем — поэтому ключ кэша это путь
        // И время изменения: смена модели подхватывается без перезапуска приложения.
        val stamp = File(path).lastModified()
        if (handle != 0L && path == loadedPath && stamp == loadedStamp) return
        if (handle != 0L) { nativeFree(handle); handle = 0L }
        handle = nativeLoadModel(path, N_CTX)
        check(handle != 0L) { "Не удалось загрузить модель: $path" }
        loadedPath = path
        loadedStamp = stamp
    }

    fun close() {
        if (handle != 0L) { nativeFree(handle); handle = 0L; loadedPath = null }
    }

    // --- JNI (реализация в app/src/main/cpp/llama_bridge.cpp) ---
    private external fun nativeLoadModel(path: String, nCtx: Int): Long
    private external fun nativeGenerate(
        handle: Long, roles: Array<String>, contents: Array<String>, nPredict: Int
    ): String
    private external fun nativeFree(handle: Long)

    companion object {
        const val N_CTX = 2048
        const val N_PREDICT = 256

        /** true, если нативная библиотека llama.cpp собрана и загружена. */
        val available: Boolean = runCatching { System.loadLibrary("llamabridge") }.isSuccess
    }
}
