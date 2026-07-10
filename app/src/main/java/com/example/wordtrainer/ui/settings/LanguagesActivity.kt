package com.example.wordtrainer.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wordtrainer.R
import com.example.wordtrainer.WordTrainerApp
import com.example.wordtrainer.data.local.LanguageEntity
import com.example.wordtrainer.databinding.ActivityLanguagesBinding
import com.example.wordtrainer.databinding.DialogAddLanguageBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class LanguagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLanguagesBinding
    private val settings by lazy { (application as WordTrainerApp).settings }
    private val repository by lazy { (application as WordTrainerApp).repository }
    private lateinit var adapter: LanguageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanguagesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = LanguageAdapter(onSelect = ::selectLanguage, onDelete = ::confirmDelete)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.addFab.setOnClickListener { showAddDialog() }

        lifecycleScope.launch {
            this@LanguagesActivity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repository.observeLanguages(), settings.language) { langs, current ->
                    langs to current
                }.collect { (langs, current) ->
                    // Если текущий язык удалён — переключаемся на первый доступный.
                    if (langs.isNotEmpty() && langs.none { it.code == current }) {
                        selectLanguage(langs.first())
                        return@collect
                    }
                    adapter.submitList(langs.map { LanguageRow(it, it.code == current) })
                }
            }
        }
    }

    private fun selectLanguage(lang: LanguageEntity) {
        settings.setLanguage(lang.code)
        lifecycleScope.launch { repository.seedIfNeeded(lang.code, settings) }
    }

    private fun confirmDelete(lang: LanguageEntity) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.lang_delete_confirm, lang.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    val ok = repository.deleteLanguage(lang.code)
                    if (!ok) toast(getString(R.string.lang_delete_last))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddLanguageBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_language)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                lifecycleScope.launch {
                    val code = repository.addLanguage(
                        dialogBinding.nameInput.text.toString(),
                        dialogBinding.localeInput.text.toString()
                    )
                    if (code != null) (application as WordTrainerApp).achievements.onLanguageAdded()
                    toast(getString(if (code != null) R.string.lang_added else R.string.lang_add_failed))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
