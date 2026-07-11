package com.example.wordtrainer.ui.coach

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wordtrainer.R
import com.example.wordtrainer.WordTrainerApp
import com.example.wordtrainer.databinding.ActivityCoachBinding
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Экран ИИ-коуча: свободный чат, уроки-диалоги и упражнение «собери предложение».
 * Изолирован от словарной части — запускается из тулбара главного экрана.
 */
class CoachActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCoachBinding

    /** Общий VM чата/уроков — фрагменты берут его через [chatViewModel]. */
    val chatViewModel: CoachViewModel by viewModels {
        val app = application as WordTrainerApp
        viewModelFactory {
            initializer { CoachViewModel(app.coachRepository, app.coachSettings, app.coachHistory) }
        }
    }

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionCallback: ((String) -> Unit)? = null
    private var listening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoachBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        requestMicPermission()
        initTts()
        initRecognizer()

        binding.toolbar.setNavigationOnClickListener { handleBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        if (savedInstanceState == null) showFragment(ChatFragment(), root = true)

        // Авто-озвучка ответов ИИ и показ ошибок.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { chatViewModel.speak.collect { speak(it) } }
                launch { chatViewModel.errors.collect { showError(it) } }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.coach_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean = when (item.itemId) {
        R.id.action_coach_lessons -> { showFragment(LessonsFragment()); true }
        R.id.action_coach_smart -> { showFragment(SmartLessonFragment()); true }
        R.id.action_coach_settings -> { CoachSettingsDialog().show(supportFragmentManager, "coach_settings"); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showFragment(fragment: Fragment, root: Boolean = false) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.coachContainer, fragment)
            .also { if (!root) it.addToBackStack(null) }
            .commit()
    }

    /** Открывает урок и возвращается к чату (он рендерит режим урока). */
    fun openLesson(lesson: com.example.wordtrainer.data.coach.Lesson) {
        chatViewModel.startLesson(lesson)
        supportFragmentManager.popBackStack()
    }

    private fun handleBack() {
        when {
            supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
            chatViewModel.state.value.inLesson -> chatViewModel.leaveLesson()
            else -> finish()
        }
    }

    // ---- Озвучка (TTS) ------------------------------------------------------

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { runOnUiThread { chatViewModel.setPlaying(null) } }
                    @Deprecated("deprecated") override fun onError(utteranceId: String?) {
                        runOnUiThread { chatViewModel.setPlaying(null) }
                    }
                })
            }
        }
    }

    fun speak(text: String) {
        chatViewModel.setPlaying(text)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        tts?.stop()
        chatViewModel.setPlaying(null)
    }

    // ---- Распознавание речи (STT) ------------------------------------------

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { listening = false; recognitionCallback = null }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { recognitionCallback?.invoke(it) }
                    listening = false
                    recognitionCallback = null
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening(onResult: (String) -> Unit) {
        if (listening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission()
            Toast.makeText(this, R.string.coach_mic_denied, Toast.LENGTH_SHORT).show()
            return
        }
        recognitionCallback = onResult
        listening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        runCatching { speechRecognizer?.startListening(intent) }
    }

    fun stopListening() {
        if (!listening) return
        listening = false
        runCatching { speechRecognizer?.stopListening() }
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, getString(R.string.coach_error, message), Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        tts?.shutdown()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}
