package com.example.wordtrainer.data.coach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Единая точка ИИ-инференса. Маршрутизирует запрос в зависимости от режима:
 * - [CoachMode.ON_DEVICE]   — llama.cpp прямо на телефоне ([LlamaEngine]);
 * - [CoachMode.LOCAL_SERVER] — HTTP к OpenAI-совместимому серверу в сети.
 */
class CoachRepository(private val settings: CoachSettingsStore) {

    private val llama by lazy { LlamaEngine() }

    private var cachedApi: CoachApi? = null
    private var cacheKey: String? = null

    private fun api(): CoachApi {
        val key = settings.baseUrl
        cachedApi?.let { if (key == cacheKey) return it }

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val api = Retrofit.Builder()
            .baseUrl(settings.baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoachApi::class.java)

        cachedApi = api
        cacheKey = key
        return api
    }

    /** Один запрос к модели с произвольным набором сообщений. */
    suspend fun complete(messages: List<CoachMessage>): String = when (settings.mode.value) {
        CoachMode.ON_DEVICE -> llama.complete(messages, settings.modelUri.value)
        CoachMode.LOCAL_SERVER -> withContext(Dispatchers.IO) {
            val response = api().getCompletion(CoachChatRequest(settings.serverModel.value, messages))
            response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        }
    }

    /** Расстановка пунктуации в тексте после голосового ввода (без правки слов). */
    suspend fun fixPunctuation(text: String): String {
        val prompt = """
            [SYSTEM]: You are a punctuation expert.
            Your ONLY task is to add missing commas, periods, and question marks to the user's text.
            1. Do NOT change any words.
            2. Do NOT correct grammar or spelling.
            3. Do NOT rewrite the sentence.
            4. Keep the original word order exactly as it is.
            5. Reply ONLY with the text and punctuation, no explanations.
        """.trimIndent()
        val result = complete(
            listOf(CoachMessage("system", prompt), CoachMessage("user", text))
        )
        return result.removeSurrounding("\"").removeSurrounding("'").trim().ifBlank { text }
    }

    /** Генерирует одно упражнение «собери предложение» по случайной теме. */
    suspend fun generateExercise(): SentenceExercise? {
        val topic = CoachContent.topics.random()
        val prompt = """
            Generate 1 unique English sentence about $topic (B1 level).
            Avoid repeats. Use JSON format: {"en": "...", "ru": "...", "explanation": "..."}
        """.trimIndent()
        val response = complete(listOf(CoachMessage("user", prompt)))
        return CoachExerciseParser.parse(response)
    }

    fun close() = llama.close()
}
