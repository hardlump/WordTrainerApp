package com.example.wordtrainer.tts

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import com.example.wordtrainer.domain.TtsMode
import java.net.URLEncoder
import java.util.Locale

/**
 * Озвучка слов. По умолчанию — оффлайн Android TTS (надёжно, без сети).
 * Онлайн-режим (неофициальный эндпоинт Google) оставлен как фолбэк и снабжён
 * User-Agent, без которого сервис часто отдаёт 403.
 *
 * Язык задаётся BCP-47 тегом ([LanguageEntity.locale]), напр. «en», «uk», «de».
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech = TextToSpeech(appContext, this)
    private var ready = false
    private var localeTag: String = "en"
    private var mediaPlayer: MediaPlayer? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            applyLocale()
        }
    }

    private fun applyLocale() {
        runCatching { tts.language = Locale.forLanguageTag(localeTag) }
    }

    /** Есть ли оффлайн-голос для языка (иначе стоит использовать онлайн). */
    fun isOfflineAvailable(localeTag: String): Boolean {
        if (!ready) return false
        return runCatching {
            tts.isLanguageAvailable(Locale.forLanguageTag(localeTag)) >= TextToSpeech.LANG_AVAILABLE
        }.getOrDefault(false)
    }

    fun speak(text: String, mode: TtsMode, localeTag: String) {
        if (text.isBlank()) return
        if (localeTag.isNotBlank() && localeTag != this.localeTag) {
            this.localeTag = localeTag
            applyLocale()
        }
        if (mode == TtsMode.OFFLINE && ready) speakOffline(text) else speakOnline(text)
    }

    private fun speakOffline(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word-id")
    }

    private fun speakOnline(text: String) {
        val tl = localeTag.substringBefore('-').ifBlank { "en" }
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
