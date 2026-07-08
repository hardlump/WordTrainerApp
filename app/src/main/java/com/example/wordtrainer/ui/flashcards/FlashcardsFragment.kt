package com.example.wordtrainer.ui.flashcards

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wordtrainer.R
import com.example.wordtrainer.databinding.FragmentFlashcardsBinding
import com.example.wordtrainer.domain.Direction
import com.example.wordtrainer.ui.app
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import kotlin.math.abs

class FlashcardsFragment : Fragment() {

    private var _binding: FragmentFlashcardsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FlashcardsViewModel by viewModels {
        viewModelFactory {
            initializer { FlashcardsViewModel(app.repository, app.settings, app.speaker) }
        }
    }

    // Состояние жеста
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var animating = false
    private var touchSlop = 0
    private var lastWordKey: Long? = null

    private val card: MaterialCardView get() = binding.card
    private val density get() = resources.displayMetrics.density

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentFlashcardsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        setupCardGestures()

        binding.knowBtn.setOnClickListener { flyOut(correct = true) }
        binding.dontKnowBtn.setOnClickListener { flyOut(correct = false) }
        binding.speakBtn.setOnClickListener { viewModel.speak() }
        binding.favoriteBtn.setOnClickListener { viewModel.toggleFavorite() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                // Смена языка/направления — перезагружаем порцию.
                launch { app.settings.language.collect { viewModel.load() } }
                launch { app.settings.direction.collect { render(viewModel.state.value) } }
            }
        }
    }

    // ---- Жесты: карточка следует за пальцем и улетает при ответе ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCardGestures() {
        card.setOnTouchListener { v, event ->
            if (animating) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(dy)) dragging = true
                    if (dragging && card.width > 0) {
                        card.translationX = dx
                        card.rotation = dx / card.width * MAX_ROTATION
                        updateSwipeHint(dx)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging) {
                        if (abs(dx) < touchSlop && abs(dy) < touchSlop) {
                            v.performClick()
                            viewModel.reveal()
                        }
                    } else if (abs(dx) > card.width * COMMIT_FRACTION) {
                        flyOut(correct = dx > 0)
                    } else {
                        springBack()
                    }
                    dragging = false
                    true
                }

                else -> false
            }
        }
    }

    /** Подсветка рамки карточки при протягивании: зелёная вправо, красная влево. */
    private fun updateSwipeHint(dx: Float) {
        val frac = (abs(dx) / (card.width * COMMIT_FRACTION)).coerceIn(0f, 1f)
        card.strokeWidth = (frac * MAX_STROKE_DP * density).toInt()
        card.setStrokeColor(color(if (dx > 0) R.color.accent_correct else R.color.accent_wrong))
    }

    private fun clearSwipeHint() {
        card.strokeWidth = 0
    }

    private fun springBack() {
        card.animate().translationX(0f).rotation(0f)
            .setDuration(if (animationsEnabled()) SPRING_MS else 0)
            .withEndAction { clearSwipeHint() }
            .start()
    }

    /** Улетание карточки в сторону ответа, затем — регистрация ответа. */
    private fun flyOut(correct: Boolean) {
        if (animating) return
        if (viewModel.state.value.current == null) return
        if (!animationsEnabled()) {
            viewModel.answer(correct)
            return
        }
        animating = true
        val width = if (card.width > 0) card.width else resources.displayMetrics.widthPixels
        card.setStrokeColor(color(if (correct) R.color.accent_correct else R.color.accent_wrong))
        card.animate()
            .translationX(if (correct) width * 1.5f else -width * 1.5f)
            .rotation(if (correct) MAX_ROTATION else -MAX_ROTATION)
            .alpha(0f)
            .setDuration(FLY_OUT_MS)
            .withEndAction {
                clearSwipeHint()
                animating = false
                viewModel.answer(correct) // сменит состояние -> render анимирует появление
            }
            .start()
    }

    private fun animateIn() {
        clearSwipeHint()
        card.translationX = 0f
        card.rotation = 0f
        if (!animationsEnabled()) {
            card.alpha = 1f
            card.scaleX = 1f
            card.scaleY = 1f
            return
        }
        card.alpha = 0f
        card.scaleX = 0.96f
        card.scaleY = 0.96f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ANIMATE_IN_MS).start()
    }

    private fun render(state: FlashState) {
        binding.emptyText.visibility = if (state.isEmpty) View.VISIBLE else View.GONE
        binding.card.visibility = if (state.isEmpty) View.INVISIBLE else View.VISIBLE

        val word = state.current
        if (word == null) {
            lastWordKey = null
            binding.promptText.text = ""
            binding.answerText.text = ""
            binding.remainingText.text = ""
            return
        }

        val forward = app.settings.direction.value == Direction.WORD_TO_TRANSLATION
        binding.promptText.text = if (forward) word.word else word.translation
        binding.answerText.text = if (forward) word.translation else word.word
        binding.answerText.visibility = if (state.revealed) View.VISIBLE else View.INVISIBLE
        binding.hintText.visibility = if (state.revealed) View.INVISIBLE else View.VISIBLE
        binding.remainingText.text = getString(R.string.remaining, state.remaining)

        val starColor = if (word.isFavorite) R.color.accent_star else R.color.text_secondary
        binding.favoriteBtn.iconTint = ColorStateList.valueOf(color(starColor))

        // Новая карточка — проявляем; та же карточка (раскрытие/избранное) — оставляем как есть.
        if (word.id != lastWordKey) {
            lastWordKey = word.id
            animateIn()
        } else if (!animating && !state.loading) {
            card.translationX = 0f
            card.rotation = 0f
            card.alpha = 1f
        }
    }

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)

    /** Учитываем системную настройку «уменьшить анимацию». */
    private fun animationsEnabled(): Boolean =
        Settings.Global.getFloat(
            requireContext().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) > 0f

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MAX_ROTATION = 12f
        const val COMMIT_FRACTION = 0.33f
        const val MAX_STROKE_DP = 4f
        const val FLY_OUT_MS = 180L
        const val ANIMATE_IN_MS = 200L
        const val SPRING_MS = 180L
    }
}
