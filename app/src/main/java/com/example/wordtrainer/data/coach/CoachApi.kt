package com.example.wordtrainer.data.coach

import retrofit2.http.Body
import retrofit2.http.POST

/** OpenAI-совместимый эндпоинт чата (Groq и локальные серверы). */
interface CoachApi {
    @POST("v1/chat/completions")
    suspend fun getCompletion(@Body request: CoachChatRequest): CoachChatResponse
}
