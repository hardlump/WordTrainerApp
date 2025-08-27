package com.example.wordtrainer

import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.*

data class Word(val word: String, val translation: String)

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var wordText: TextView
    private lateinit var translationText: TextView
    private lateinit var showTranslationBtn: Button
    private lateinit var prevBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var switchSourceBtn: Button
    private lateinit var speakBtn: Button

    private var words = listOf<Word>()
    private var currentIndex = 0
    private var useJson = false // false — CSV, true — JSON

    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var useOfflineTts = true // true — Android TTS, false — Google Translate TTS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wordText = findViewById(R.id.wordText)
        translationText = findViewById(R.id.translationText)
        showTranslationBtn = findViewById(R.id.showTranslationBtn)
        prevBtn = findViewById(R.id.prevBtn)
        nextBtn = findViewById(R.id.nextBtn)
        switchSourceBtn = findViewById(R.id.switchSourceBtn)
        speakBtn = findViewById(R.id.speakBtn)

        tts = TextToSpeech(this, this)

        updateSourceButtonText()
        loadWordsFromCurrentSource()

        showTranslationBtn.setOnClickListener {
            translationText.text = words[currentIndex].translation
        }

        nextBtn.setOnClickListener {
            currentIndex++
            if (currentIndex >= words.size) {
                words = words.shuffled()
                currentIndex = 0
            }
            showWord()
        }

        prevBtn.setOnClickListener {
            currentIndex--
            if (currentIndex < 0) {
                words = words.shuffled()
                currentIndex = words.size - 1
            }
            showWord()
        }

        switchSourceBtn.setOnClickListener {
            useJson = !useJson
            updateSourceButtonText()
            loadWordsFromCurrentSource()
        }

        speakBtn.setOnClickListener {
            val word = words[currentIndex].word
            if (useOfflineTts && ttsReady) {
                speakOffline(word)
            } else {
                speakOnline(word)
            }
        }

        wordText.setOnClickListener {
            // Переключаем режим озвучивания между оффлайн и Google TTS
            useOfflineTts = !useOfflineTts
        }
    }

    private fun updateSourceButtonText() {
        switchSourceBtn.text = if (useJson) "Словарь" else "Мои слова"
    }

    private fun loadWordsFromCurrentSource() {
        words = if (useJson) loadWordsFromJson() else loadWordsFromCsv()
        words = words.shuffled()
        currentIndex = 0
        showWord()
    }

    private fun showWord() {
        wordText.text = words[currentIndex].word
        translationText.text = ""
    }

    private fun loadWordsFromCsv(): List<Word> {
        val inputStream = resources.openRawResource(R.raw.words)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val list = mutableListOf<Word>()
        reader.useLines { lines ->
            lines.drop(1).forEach {
                val parts = it.split(",")
                if (parts.size >= 4) {
                    val word = parts[0].trim('\'', ' ')
                    val translation = parts.drop(3).joinToString(",").trim('\'', ' ', '"')
                    list.add(Word(word, translation))
                }
            }
        }
        return list
    }

    private fun loadWordsFromJson(): List<Word> {
        val inputStream = assets.open("words.json")
        val json = inputStream.readBytes().toString(Charset.defaultCharset())
        val jsonArray = JSONArray(json)
        val list = mutableListOf<Word>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(Word(obj.getString("word"), obj.getString("translation")))
        }
        return list
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.ENGLISH
            ttsReady = true
        }
    }

    private fun speakOffline(word: String) {
        tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun speakOnline(word: String) {
        val url = "https://translate.google.com/translate_tts?ie=UTF-8&q=${URLEncoder.encode(word, "UTF-8")}&tl=en&client=gtx"
        try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(url)
            mediaPlayer.prepare()
            mediaPlayer.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
