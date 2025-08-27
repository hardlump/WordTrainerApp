
package com.example.wordtrainer

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.nio.charset.Charset

data class Word(val word: String, val translation: String)

class MainActivity : AppCompatActivity() {

    private lateinit var wordText: TextView
    private lateinit var translationText: TextView
    private lateinit var showTranslationBtn: Button
    private lateinit var prevBtn: Button
    private lateinit var nextBtn: Button

    private var words = listOf<Word>()
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wordText = findViewById(R.id.wordText)
        translationText = findViewById(R.id.translationText)
        showTranslationBtn = findViewById(R.id.showTranslationBtn)
        prevBtn = findViewById(R.id.prevBtn)
        nextBtn = findViewById(R.id.nextBtn)

        // Load words from JSON file
        words = loadWordsFromJson()

        if (words.isNotEmpty()) {
            currentIndex = (words.indices).random()
            showWord()
        }

        showTranslationBtn.setOnClickListener {
            translationText.text = words[currentIndex].translation
        }

        nextBtn.setOnClickListener {
            currentIndex = (currentIndex + 1) % words.size
            showWord()
        }

        prevBtn.setOnClickListener {
            currentIndex = if (currentIndex - 1 < 0) words.size - 1 else currentIndex - 1
            showWord()
        }
    }

    private fun showWord() {
        wordText.text = words[currentIndex].word
        translationText.text = ""
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
