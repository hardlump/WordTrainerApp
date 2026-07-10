package com.example.wordtrainer.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.wordtrainer.R
import kotlin.math.min

/** Тепловая карта активности в стиле GitHub: столбцы — недели, строки — дни недели. */
class HeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var weeks: List<List<Int?>> = emptyList()
    private var maxCount = 1

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val filled = ContextCompat.getColor(context, R.color.accent_teal)
    private val zero = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.text_secondary), 30)
    private val rect = RectF()

    fun setData(data: List<List<Int?>>) {
        weeks = data
        maxCount = data.flatten().filterNotNull().maxOrNull()?.coerceAtLeast(1) ?: 1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cols = weeks.size
        if (cols == 0) return

        val gap = 2.dp
        val cell = min((width - gap * (cols - 1)) / cols, (height - gap * 6) / 7f)
        val radius = cell * 0.2f

        for (c in 0 until cols) {
            val column = weeks[c]
            for (r in 0 until 7) {
                val value = column.getOrNull(r) ?: continue
                val x = c * (cell + gap)
                val y = r * (cell + gap)
                rect.set(x, y, x + cell, y + cell)
                paint.color = colorFor(value)
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }
    }

    /** 0 — бледная ячейка; далее 4 уровня насыщенности teal по количеству повторений. */
    private fun colorFor(count: Int): Int {
        if (count <= 0) return zero
        val level = 1 + (count * 3 / maxCount).coerceIn(0, 3)
        val alpha = when (level) {
            1 -> 80
            2 -> 140
            3 -> 200
            else -> 255
        }
        return ColorUtils.setAlphaComponent(filled, alpha)
    }

    private val Int.dp: Float get() = this * resources.displayMetrics.density
}
