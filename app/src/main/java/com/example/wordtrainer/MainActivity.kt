package com.example.wordtrainer

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset

data class Word(val word: String, val translation: String)

class MainActivity : AppCompatActivity() {

    private lateinit var wordText: TextView
    private lateinit var translationText: TextView
    private lateinit var showTranslationBtn: Button
    private lateinit var prevBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var switchSourceBtn: Button

    private var words = listOf<Word>()
    private var currentIndex = 0
    private var useJson = false // false — CSV, true — JSON

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wordText = findViewById(R.id.wordText)
        translationText = findViewById(R.id.translationText)
        showTranslationBtn = findViewById(R.id.showTranslationBtn)
        prevBtn = findViewById(R.id.prevBtn)
        nextBtn = findViewById(R.id.nextBtn)
        switchSourceBtn = findViewById(R.id.switchSourceBtn)

        updateSourceButtonText()
        loadWordsFromCurrentSource()

        showTranslationBtn.setOnClickListener {
            // Показываем весь перевод
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
    }

    private fun updateSourceButtonText() {
        switchSourceBtn.text = if (useJson) "Словарь" else "Мои слова"
    }

    private fun loadWordsFromCurrentSource() {
        words = if (useJson) {
            loadWordsFromJson()
        } else {
            loadWordsFromCsv()
        }.shuffled()
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
            lines.drop(1).forEach { // пропускаем заголовок
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
}
