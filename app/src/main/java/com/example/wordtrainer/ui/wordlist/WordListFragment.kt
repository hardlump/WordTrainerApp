package com.example.wordtrainer.ui.wordlist

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wordtrainer.R
import com.example.wordtrainer.data.local.WordEntity
import com.example.wordtrainer.databinding.DialogAddWordBinding
import com.example.wordtrainer.databinding.FragmentWordlistBinding
import com.example.wordtrainer.ui.app
import kotlinx.coroutines.launch

class WordListFragment : Fragment() {

    private var _binding: FragmentWordlistBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WordListViewModel by viewModels {
        viewModelFactory {
            initializer { WordListViewModel(app.repository, app.settings) }
        }
    }

    private lateinit var adapter: WordAdapter
    private var pendingExport: String = ""

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            viewModel.importJson(uri) { added ->
                val msg = if (added > 0) getString(R.string.imported_count, added)
                else getString(R.string.import_failed)
                toast(msg)
            }
        }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            runCatching {
                requireContext().contentResolver.openOutputStream(uri)?.use {
                    it.write(pendingExport.toByteArray(Charsets.UTF_8))
                }
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentWordlistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = WordAdapter(onStar = viewModel::toggleFavorite, onLongPress = ::confirmDelete)
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.searchInput.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }
        binding.favoritesSwitch.setOnCheckedChangeListener { _, checked -> viewModel.setFavoritesOnly(checked) }
        binding.addFab.setOnClickListener { showAddDialog() }
        binding.importBtn.setOnClickListener { importLauncher.launch(arrayOf("application/json")) }
        binding.exportBtn.setOnClickListener {
            viewModel.exportJson { json ->
                pendingExport = json
                val name = "words_${app.settings.language.value.code.lowercase()}.json"
                exportLauncher.launch(name)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.words.collect { list ->
                    adapter.submitList(list)
                    binding.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddWordBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_word)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                viewModel.addWord(
                    dialogBinding.wordInput.text.toString(),
                    dialogBinding.translationInput.text.toString()
                ) { ok ->
                    toast(getString(if (ok) R.string.word_added else R.string.word_exists))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(word: WordEntity) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.delete_confirm, word.word))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(word) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
