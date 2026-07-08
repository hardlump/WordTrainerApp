package com.example.wordtrainer.ui.detail

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.wordtrainer.R
import com.example.wordtrainer.WordTrainerApp
import com.example.wordtrainer.data.local.WordEntity
import com.example.wordtrainer.databinding.ActivityWordDetailBinding
import com.example.wordtrainer.databinding.DialogAddWordBinding
import com.example.wordtrainer.domain.Leitner
import com.example.wordtrainer.ui.boxIndicator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WordDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWordDetailBinding
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    private val viewModel: WordDetailViewModel by lazy {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as WordTrainerApp
                @Suppress("UNCHECKED_CAST")
                return WordDetailViewModel(app.repository, app.settings, app.speaker) as T
            }
        }
        ViewModelProvider(this, factory)[WordDetailViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWordDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val id = intent.getLongExtra(EXTRA_WORD_ID, 0L)
        viewModel.load(id)

        binding.speakBtn.setOnClickListener { viewModel.speak() }
        binding.favoriteBtn.setOnClickListener { viewModel.toggleFavorite() }
        binding.editBtn.setOnClickListener { showEditDialog() }
        binding.resetBtn.setOnClickListener { viewModel.resetProgress() }
        binding.markLearnedBtn.setOnClickListener { viewModel.markLearned() }
        binding.deleteBtn.setOnClickListener { confirmDelete() }

        lifecycleScope.launch {
            this@WordDetailActivity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.word.collect { word -> if (word != null) render(word) }
            }
        }
    }

    private fun render(word: WordEntity) {
        binding.wordText.text = word.word
        binding.translationText.text = word.translation

        binding.favoriteBtn.text =
            getString(if (word.isFavorite) R.string.detail_fav_remove else R.string.detail_fav_add)
        val starColor = if (word.isFavorite) R.color.accent_star else R.color.text_secondary
        binding.favoriteBtn.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, starColor))

        binding.boxText.text = getString(R.string.detail_box, word.boxIndicator())
        binding.statusText.text = getString(statusRes(word))

        val now = System.currentTimeMillis()
        binding.nextDueText.text = if (word.nextDueAt <= now) {
            getString(R.string.detail_next_due_now)
        } else {
            getString(R.string.detail_next_due, dateFormat.format(Date(word.nextDueAt)))
        }
        binding.lastReviewedText.text = getString(
            R.string.detail_last_reviewed,
            word.lastReviewedAt?.let { dateFormat.format(Date(it)) } ?: getString(R.string.detail_never)
        )

        binding.answersText.text = getString(R.string.detail_answers, word.correctCount, word.wrongCount)
        val total = word.correctCount + word.wrongCount
        val accuracy = if (total == 0) 0 else word.correctCount * 100 / total
        binding.accuracyText.text = getString(R.string.detail_accuracy, accuracy)
        binding.createdText.text = getString(R.string.detail_created, dateFormat.format(Date(word.createdAt)))
    }

    private fun statusRes(word: WordEntity): Int = when {
        word.box >= Leitner.LEARNED_BOX -> R.string.detail_status_learned
        word.box == 1 && word.lastReviewedAt == null -> R.string.detail_status_new
        else -> R.string.detail_status_learning
    }

    private fun showEditDialog() {
        val word = viewModel.word.value ?: return
        val dialogBinding = DialogAddWordBinding.inflate(layoutInflater)
        dialogBinding.wordInput.setText(word.word)
        dialogBinding.translationInput.setText(word.translation)
        AlertDialog.Builder(this)
            .setTitle(R.string.detail_edit)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                viewModel.edit(
                    dialogBinding.wordInput.text.toString(),
                    dialogBinding.translationInput.text.toString()
                ) { ok ->
                    Toast.makeText(
                        this,
                        getString(if (ok) R.string.word_updated else R.string.word_update_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete() {
        val word = viewModel.word.value ?: return
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_confirm, word.word))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete { finish() } }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_WORD_ID = "word_id"
    }
}
