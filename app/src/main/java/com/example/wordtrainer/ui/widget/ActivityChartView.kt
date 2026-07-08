package com.example.wordtrainer.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.wordtrainer.R
import com.example.wordtrainer.domain.DayActivity
import kotlin.math.max

/** Простой столбчатый график активности по дням, рисуется на Canvas. */
class ActivityChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var data: List<DayActivity> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent_teal)
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        alpha = 40
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textAlign = Paint.Align.CENTER
        textSize = 11.dp
    }

    private val barRect = RectF()

    fun setData(value: List<DayActivity>) {
        data = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = data.size
        if (n == 0) return

        val labelArea = 16.dp
        val chartH = height - labelArea
        val slot = width.toFloat() / n
        val barW = slot * 0.55f
        val radius = barW / 2f
        val maxCount = max(data.maxOf { it.count }, 1)
        val minVisible = 3.dp

        data.forEachIndexed { i, day ->
            val cx = slot * i + slot / 2f
            val left = cx - barW / 2f
            val right = cx + barW / 2f

            if (day.count == 0) {
                // Пустой день — тонкая «дорожка» у оси.
                barRect.set(left, chartH - minVisible, right, chartH)
                canvas.drawRoundRect(barRect, radius, radius, trackPaint)
            } else {
                val h = max(chartH * day.count / maxCount, minVisible)
                barRect.set(left, chartH - h, right, chartH)
                canvas.drawRoundRect(barRect, radius, radius, barPaint)
            }
            canvas.drawText(day.label, cx, height - 2.dp, labelPaint)
        }
    }

    private val Int.dp: Float get() = this * resources.displayMetrics.density
}
