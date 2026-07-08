package com.example.wordtrainer.tts

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import com.example.wordtrainer.domain.Language
import com.example.wordtrainer.domain.TtsMode
import java.net.URLEncoder
import java.util.Locale

/**
 * Озвучка слов. По умолчанию — оффлайн Android TTS (надёжно, без сети).
 * Онлайн-режим (неофициальный эндпоинт Google) оставлен как фолбэк и снабжён
 * User-Agent, без которого сервис часто отдаёт 403.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech = TextToSpeech(appContext, this)
    private var ready = false
    private var language: Language = Language.EN
    private var mediaPlayer: MediaPlayer? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            applyLocale()
        }
    }

    fun setLanguage(lang: Language) {
        language = lang
        if (ready) applyLocale()
    }

    private fun applyLocale() {
        tts.language = if (language == Language.EN) Locale.ENGLISH else Locale("uk", "UA")
    }

    fun speak(text: String, mode: TtsMode) {
        if (text.isBlank()) return
        val offlineWorks = mode == TtsMode.OFFLINE && ready
        if (offlineWorks) speakOffline(text) else speakOnline(text)
    }

    private fun speakOffline(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word-id")
    }

    private fun speakOnline(text: String) {
        val tl = if (language == Language.EN) "en" else "uk"
        val url = "https://translate.google.com/translate_tts?ie=UTF-8" +
            "&q=${URLEncoder.encode(text, "UTF-8")}&tl=$tl&client=gtx"
        try {
            releasePlayer()
            mediaPlayer = MediaPlayer().apply {
                val headers = mapOf("User-Agent" to "Mozilla/5.0")
                setDataSource(appContext, Uri.parse(url), headers)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { releasePlayer() }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun shutdown() {
        releasePlayer()
        tts.stop()
        tts.shutdown()
    }
}
