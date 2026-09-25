package com.example.studybuddy.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class BarData(val label: String, val value: Int)

    private var data: Array<BarData> = emptyArray()
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6200EE.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF757575.toInt()
        textSize = 11 * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF212121.toInt()
        textSize = 12 * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
    }

    fun setData(newData: Array<BarData>) {
        data = newData
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val maxValue = (data.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
        val chartHeight = height - 60f
        val barWidth = width / (data.size * 2f)
        val spacing = width / (data.size.toFloat())

        data.forEachIndexed { index, bar ->
            val centerX = spacing * index + spacing / 2f
            val barHeight = (bar.value.toFloat() / maxValue) * chartHeight
            val top = chartHeight - barHeight

            val rect = RectF(
                centerX - barWidth / 2f,
                top,
                centerX + barWidth / 2f,
                chartHeight
            )
            canvas.drawRoundRect(rect, 8f, 8f, if (bar.value > 0) barPaint else emptyPaint)

            if (bar.value > 0) {
                canvas.drawText(
                    bar.value.toString(),
                    centerX,
                    top - 6f,
                    valuePaint
                )
            }
            canvas.drawText(
                bar.label,
                centerX,
                height - 12f,
                textPaint
            )
        }
    }
}