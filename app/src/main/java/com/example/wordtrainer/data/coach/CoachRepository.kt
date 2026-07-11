package com.example.wordtrainer.data.coach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Доступ к ИИ (Groq или локальный OpenAI-совместимый сервер). Держит один
 * закэшированный Retrofit/OkHttp и пересоздаёт его только при смене
 * baseUrl/ключа, а не на каждый запрос.
 */
class CoachRepository(private val settings: CoachSettingsStore) {

    private var cachedApi: CoachApi? = null
    private var cacheKey: String? = null

    private fun api(): CoachApi {
        val key = "${settings.baseUrl}|${settings.isLocal}|${settings.groqApiKey.value}"
        cachedApi?.let { if (key == cacheKey) return it }

        val client = OkHttpClient.Builder().apply {
            connectTimeout(60, TimeUnit.SECONDS)
            readTimeout(120, TimeUnit.SECONDS)
            writeTimeout(60, TimeUnit.SECONDS)
            if (!settings.isLocal) {
                val token = settings.groqApiKey.value
                addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                    chain.proceed(req)
                }
            }
        }.build()

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
    suspend fun complete(messages: List<CoachMessage>): String = withContext(Dispatchers.IO) {
        val response = api().getCompletion(CoachChatRequest(settings.currentModel, messages))
        response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
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
}
