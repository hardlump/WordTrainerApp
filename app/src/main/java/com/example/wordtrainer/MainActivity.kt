package com.example.wordtrainer

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.io.*
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
    private lateinit var importJsonBtn: Button   // <-- имя совпадает с id в XML

    private var words = listOf<Word>()
    private var currentIndex = 0
    private var useJson = false // false — CSV, true — JSON

    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var useOfflineTts = true // true — Android TTS, false — Google Translate TTS

    private val PICK_JSON_FILE = 1001

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
        importJsonBtn = findViewById(R.id.importJsonBtn) // <-- правильный id

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
            val word = words.getOrNull(currentIndex)?.word ?: return@setOnClickListener
            if (useOfflineTts && ttsReady) {
                speakOffline(word)
            } else {
                speakOnline(word)
            }
        }

        wordText.setOnClickListener {
            useOfflineTts = !useOfflineTts
            Toast.makeText(
                this,
                if (useOfflineTts) "Озвучка: оффлайн (Android TTS)" else "Озвучка: онлайн (Google TTS)",
                Toast.LENGTH_SHORT
            ).show()
        }

        importJsonBtn.setOnClickListener {
            openFilePicker()
        }
    }

    private fun updateSourceButtonText() {
        switchSourceBtn.text = if (useJson) "Словарь" else "Мои слова"
    }

    private fun loadWordsFromCurrentSource() {
        words = try {
            if (useJson) loadWordsFromJson() else loadWordsFromCsv()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка загрузки слов", Toast.LENGTH_SHORT).show()
            emptyList()
        }
        if (words.isEmpty()) {
            wordText.text = "Нет данных"
            translationText.text = ""
            return
        }
        words = words.shuffled()
        currentIndex = 0
        showWord()
    }

    private fun showWord() {
        if (words.isEmpty()) return
        wordText.text = words[currentIndex].word
        translationText.text = ""
    }

    private fun loadWordsFromCsv(): List<Word> {
        val inputStream = resources.openRawResource(R.raw.words)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val list = mutableListOf<Word>()
        reader.useLines { lines ->
            lines.drop(1).forEach { line ->
                // Простой парсер: 0=source,1=pos,2=transcription,3+=translations
                val parts = line.split(",")
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
        val file = File(filesDir, "words.json")
        val json: String = if (file.exists()) {
            file.readText()
        } else {
            // Фолбэк к ассету, если пользователь ещё не импортировал файл
            assets.open("words.json").readBytes().toString(Charset.defaultCharset())
        }
        val jsonArray = JSONArray(json)
        val list = mutableListOf<Word>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(Word(obj.getString("word"), obj.getString("translation")))
        }
        return list
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            // Разрешение на чтение (копируем в внутреннюю память)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_JSON_FILE)
    }

    @Deprecated("startActivityForResult deprecated, ok for now")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_JSON_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val ok = saveImportedFile(uri)
                if (ok) {
                    Toast.makeText(this, "JSON импортирован", Toast.LENGTH_SHORT).show()
                    // Сразу переключаемся на JSON и перезагружаем список
                    useJson = true
                    updateSourceButtonText()
                    loadWordsFromCurrentSource()
                } else {
                    Toast.makeText(this, "Не удалось импортировать файл", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveImportedFile(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri).use { input ->
                val outputFile = File(filesDir, "words.json")
                FileOutputStream(outputFile).use { output ->
                    if (input != null) input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.ENGLISH
            ttsReady = true
        }
    }

    private fun speakOffline(word: String) {
        tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word-id")
    }

    private fun speakOnline(word: String) {
        val url =
            "https://translate.google.com/translate_tts?ie=UTF-8&q=${URLEncoder.encode(word, "UTF-8")}&tl=en&client=gtx"
        try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(url)
            mediaPlayer.setOnPreparedListener { it.start() }
            mediaPlayer.setOnCompletionListener { it.release() }
            mediaPlayer.prepareAsync() // не блокируем UI
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Не удалось воспроизвести аудио", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        if (this::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
