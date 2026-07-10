package com.example.wordtrainer.ui.dictionary

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wordtrainer.R
import com.example.wordtrainer.WordTrainerApp
import com.example.wordtrainer.databinding.ActivityDictionaryBinding
import kotlinx.coroutines.launch

class DictionaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDictionaryBinding
    private lateinit var adapter: DictionaryAdapter

    private val viewModel: DictionaryViewModel by lazy {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as WordTrainerApp
                @Suppress("UNCHECKED_CAST")
                return DictionaryViewModel(app.repository, app.settings) as T
            }
        }
        ViewModelProvider(this, factory)[DictionaryViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = DictionaryAdapter(onAdd = ::addToDeck)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.searchInput.doAfterTextChanged { viewModel.search(it?.toString().orEmpty()) }

        viewModel.start()
        lifecycleScope.launch {
            this@DictionaryActivity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progress.visibility = if (state.loading) View.VISIBLE else View.GONE
                    val showEmpty = !state.loading && state.results.isEmpty()
                    binding.emptyText.visibility = if (showEmpty) View.VISIBLE else View.GONE
                    adapter.submitList(state.results)
                }
            }
        }
    }

    private fun addToDeck(entry: com.example.wordtrainer.data.local.DictionaryEntry) {
        viewModel.add(entry) { ok ->
            if (ok) (application as WordTrainerApp).achievements.onDictionaryAdd()
            Toast.makeText(
                this,
                getString(if (ok) R.string.dict_added else R.string.dict_exists),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
