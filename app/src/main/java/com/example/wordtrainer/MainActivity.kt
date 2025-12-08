package com.example.wordtrainer

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.io.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets // ИСПРАВЛЕНИЕ: Используем StandardCharsets для надежной кодировки
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
    private lateinit var importJsonBtn: Button
    private lateinit var langSwitchBtn: Button

    private var words = listOf<Word>()
    private var currentIndex = 0
    private var useJson = false // false — CSV, true — JSON

    private var currentLanguage = "EN" // ТЕКУЩИЙ ЯЗЫК: EN или UA

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
        importJsonBtn = findViewById(R.id.importJsonBtn)
        langSwitchBtn = findViewById(R.id.langSwitchBtn)

        tts = TextToSpeech(this, this)

        updateSourceButtonText()
        updateLangButtonText()
        loadWordsFromCurrentSource()

        showTranslationBtn.setOnClickListener {
            translationText.text = words[currentIndex].translation
        }

        // 🟢 ЛОГИКА NEXT BTN: Последовательный переход + Перемешивание при достижении конца
        nextBtn.setOnClickListener {
            if (words.isEmpty()) return@setOnClickListener
            currentIndex++
            if (currentIndex >= words.size) {
                words = words.shuffled() // ПЕРЕМЕШИВАНИЕ
                currentIndex = 0
                Toast.makeText(this, "Список перемешан заново!", Toast.LENGTH_SHORT).show()
            }
            showWord()
        }

        // ⬅️ ЛОГИКА PREV BTN: Последовательный переход назад + Перемешивание при достижении начала
        prevBtn.setOnClickListener {
            if (words.isEmpty()) return@setOnClickListener
            currentIndex--
            if (currentIndex < 0) {
                words = words.shuffled() // ПЕРЕМЕШИВАНИЕ
                currentIndex = words.size - 1
                Toast.makeText(this, "Список перемешан заново!", Toast.LENGTH_SHORT).show()
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

        // 🌐 Обработчик для переключения языка
        langSwitchBtn.setOnClickListener {
            showLanguagePopupMenu()
        }
    }

    private fun updateSourceButtonText() {
        switchSourceBtn.text = if (useJson) "Словарь" else "Мои слова"
    }

    private fun updateLangButtonText() {
        langSwitchBtn.text = "🌐 $currentLanguage"
    }

    private fun showLanguagePopupMenu() {
        val popup = PopupMenu(this, langSwitchBtn)
        popup.menu.add(Menu.NONE, 1, Menu.NONE, "EN (English)")
        popup.menu.add(Menu.NONE, 2, Menu.NONE, "UA (Українська)")

        popup.setOnMenuItemClickListener { item: MenuItem? ->
            when (item?.itemId) {
                1 -> { // EN
                    currentLanguage = "EN"
                    loadWordsFromCurrentSource()
                    updateLangButtonText()
                    if (this::tts.isInitialized) tts.language = Locale.ENGLISH
                    true
                }
                2 -> { // UA
                    currentLanguage = "UA"
                    loadWordsFromCurrentSource()
                    updateLangButtonText()
                    if (this::tts.isInitialized) tts.language = Locale("uk", "UA")
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun loadWordsFromCurrentSource() {
        words = try {
            if (useJson) loadWordsFromJson() else loadWordsFromCsv()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка загрузки слов для $currentLanguage", Toast.LENGTH_SHORT).show()
            emptyList()
        }

        if (words.isEmpty()) {
            wordText.text = "Нет данных для $currentLanguage"
            translationText.text = ""
            return
        }
        // ПЕРВОЕ ПЕРЕМЕШИВАНИЕ (при загрузке)
        words = words.shuffled()
        currentIndex = 0
        showWord()
        Toast.makeText(this, "Загружен и перемешан словарь: ${words.size} слов ($currentLanguage)", Toast.LENGTH_SHORT).show()
    }

    private fun showWord() {
        if (words.isEmpty()) return
        wordText.text = words[currentIndex].word
        translationText.text = ""
    }

    private fun loadWordsFromCsv(): List<Word> {
        val fileNameId = if (currentLanguage == "EN") R.raw.words else R.raw.words_ua

        val inputStream = try {
            resources.openRawResource(fileNameId)
        } catch (e: Exception) {
            Toast.makeText(this, "CSV-файл (raw/${if (currentLanguage == "EN") "words" else "words_ua"}) не найден.", Toast.LENGTH_LONG).show()
            return emptyList()
        }

        val reader = BufferedReader(InputStreamReader(inputStream))
        val list = mutableListOf<Word>()
        reader.useLines { lines ->
            lines.drop(1).forEach { line ->
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
        val jsonFileName = if (currentLanguage == "EN") "words_en.json" else "words_ua.json"

        val file = File(filesDir, jsonFileName)
        val json: String = if (file.exists()) {
            // ✅ ИСПРАВЛЕНИЕ: Чтение из внутренней памяти с явным указанием UTF-8
            try {
                file.readText(StandardCharsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
                return emptyList()
            }
        } else {
            // Фолбэк к ассету, если файл не импортирован
            try {
                // ✅ ИСПРАВЛЕНИЕ: Чтение из assets с явным указанием UTF-8
                assets.open(jsonFileName).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } catch (e: Exception) {
                return emptyList()
            }
        }

        if (json.isBlank()) return emptyList()

        val jsonArray = JSONArray(json)
        val list = mutableListOf<Word>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            // Проверка на наличие ключей
            if (obj.has("word") && obj.has("translation")) {
                list.add(Word(obj.getString("word"), obj.getString("translation")))
            }
        }
        return list
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
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
        val jsonFileName = if (currentLanguage == "EN") "words_en.json" else "words_ua.json"

        return try {
            contentResolver.openInputStream(uri).use { input ->
                if (input == null) return false

                val outputFile = File(filesDir, jsonFileName)
                // ✅ ИСПРАВЛЕНИЕ: Использование Buffered потоков для более надежного копирования
                BufferedInputStream(input).use { bufferedInput ->
                    FileOutputStream(outputFile).use { output ->
                        bufferedInput.copyTo(output)
                    }
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
            if (currentLanguage == "EN") {
                tts.language = Locale.ENGLISH
            } else {
                tts.language = Locale("uk", "UA")
            }
            ttsReady = true
        }
    }

    private fun speakOffline(word: String) {
        tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word-id")
    }

    private fun speakOnline(word: String) {
        val ttsLangCode = if (currentLanguage == "EN") "en" else "uk"

        val url =
            "https://translate.google.com/translate_tts?ie=UTF-8&q=${URLEncoder.encode(word, "UTF-8")}&tl=$ttsLangCode&client=gtx"
        try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(url)
            mediaPlayer.setOnPreparedListener { it.start() }
            mediaPlayer.setOnCompletionListener { it.release() }
            mediaPlayer.prepareAsync()
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