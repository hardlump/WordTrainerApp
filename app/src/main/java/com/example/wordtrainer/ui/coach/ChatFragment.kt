package com.example.wordtrainer.ui.coach

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wordtrainer.R
import com.example.wordtrainer.databinding.FragmentCoachChatBinding
import kotlinx.coroutines.launch

/** Свободный чат с ИИ-учителем и он же — экран активного урока-диалога. */
class ChatFragment : Fragment() {

    private var _binding: FragmentCoachChatBinding? = null
    private val binding get() = _binding!!

    private val coach get() = requireActivity() as CoachActivity
    private val vm get() = coach.chatViewModel

    private lateinit var adapter: ChatAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCoachChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ChatAdapter(
            onTranslate = ::openTranslate,
            onSpeakToggle = { row ->
                if (row.isPlaying) coach.stopSpeaking() else coach.speak(row.message.content)
            }
        )
        binding.messagesRecycler.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        binding.messagesRecycler.adapter = adapter

        binding.sendBtn.setOnClickListener { submit() }
        binding.finishLessonBtn.setOnClickListener { vm.finishLesson() }

        binding.micBtn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.micBtn.text = getString(R.string.coach_release_to_send)
                    coach.startListening { result -> vm.sendVoice(result) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.micBtn.text = getString(R.string.coach_hold_to_speak)
                    coach.stopListening()
                    v.performClick()
                    true
                }
                else -> false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }
    }

    private fun submit() {
        val text = binding.messageInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        binding.messageInput.text?.clear()
        vm.sendText(text)
    }

    private fun render(state: CoachChatState) {
        val rows = state.messages.map { ChatRow(it, it.content == state.playing) }
        adapter.submitList(rows) {
            if (rows.isNotEmpty()) binding.messagesRecycler.scrollToPosition(rows.size - 1)
        }

        binding.lessonProgressPanel.visibility = if (state.inLesson) View.VISIBLE else View.GONE
        if (state.inLesson) {
            val percent = (state.progress * 100).toInt()
            binding.lessonProgressPercent.text = "$percent%"
            binding.lessonProgressBar.setProgressCompat(percent, true)
            binding.lessonProgressLabel.setText(
                if (state.finished) R.string.coach_lesson_complete else R.string.coach_lesson_progress
            )
        }

        binding.thinkingText.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.thinkingBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        val lessonDone = state.inLesson && state.finished
        binding.finishLessonBtn.visibility = if (lessonDone) View.VISIBLE else View.GONE
        binding.inputPanel.visibility = if (lessonDone) View.GONE else View.VISIBLE
    }

    private fun openTranslate(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            `package` = "com.google.android.apps.translate"
        }
        runCatching { startActivity(intent) }.onFailure {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://translate.google.com/?text=${Uri.encode(text)}"))
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
