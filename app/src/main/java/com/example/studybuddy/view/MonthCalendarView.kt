package com.example.studybuddy.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.studybuddy.R
import java.util.Calendar

class MonthCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var month: Calendar = Calendar.getInstance()
        private set

    private val dayBackground: Set<Long> = emptySet()
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1D1B20.toInt()
        textSize = 14 * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }
    private val mutedDayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB0AEB7.toInt()
        textSize = 14 * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 14 * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val todayBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6750A4.toInt()
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6750A4.toInt()
    }
    private val dotTodayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF757575.toInt()
        textSize = 12 * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    var selectedDay: Calendar? = null
        internal set
    var onDaySelected: ((Calendar) -> Unit)? = null

    private var dayBackgrounds: Set<Long> = emptySet()

    init {
        val firstDay = Calendar.getInstance()
        firstDay.set(Calendar.DAY_OF_MONTH, 1)
        month = firstDay
    }

    fun setMonth(newMonth: Calendar) {
        month = newMonth
        invalidate()
    }

    fun setDayBackgrounds(days: Set<Long>) {
        dayBackgrounds = days
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, width)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellWidth = width / 7f
        val cellHeight = height / 7f

        val dayNames = resources.getStringArray(R.array.day_names_short)
        dayNames.forEachIndexed { index, name ->
            canvas.drawText(name, cellWidth * index + cellWidth / 2f, cellHeight / 2f + 4f, headerPaint)
        }

        val firstDayOfMonth = Calendar.getInstance().apply {
            timeInMillis = month.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val daysInMonth = firstDayOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayIndex = firstDayOfMonth.get(Calendar.DAY_OF_WEEK) - 1

        val today = Calendar.getInstance()
        val isCurrentMonth =
            today.get(Calendar.YEAR) == month.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == month.get(Calendar.MONTH)

        for (day in 1..daysInMonth) {
            val cellIndex = firstDayIndex + day - 1
            val row = cellIndex / 7
            val col = cellIndex % 7
            val cx = cellWidth * col + cellWidth / 2f
            val cy = cellHeight * (row + 1) + cellHeight / 2f

            val dayCal = Calendar.getInstance().apply {
                timeInMillis = firstDayOfMonth.timeInMillis
                set(Calendar.DAY_OF_MONTH, day)
            }
            val dayKey = dayCal.timeInMillis / 86400000L
            val hasTasks = dayKey in dayBackgrounds
            val isToday = isCurrentMonth && day == today.get(Calendar.DAY_OF_MONTH)
            val isSelected = selectedDay?.let {
                it.get(Calendar.YEAR) == month.get(Calendar.YEAR) &&
                    it.get(Calendar.MONTH) == month.get(Calendar.MONTH) &&
                    it.get(Calendar.DAY_OF_MONTH) == day
            } == true

            if (isToday) {
                canvas.drawCircle(cx, cy, cellWidth * 0.32f, todayBgPaint)
            } else if (isSelected) {
                canvas.drawCircle(cx, cy, cellWidth * 0.32f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFEADDFF.toInt()
                })
            }

            val paint = when {
                isToday -> todayPaint
                !isToday && hasTasks -> dayPaint
                else -> mutedDayPaint
            }
            canvas.drawText(day.toString(), cx, cy + 5f, paint)

            if (hasTasks) {
                canvas.drawCircle(cx, cy + cellHeight * 0.32f, 3f, if (isToday) dotTodayPaint else dotPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val cellWidth = width / 7f
            val cellHeight = height / 7f
            val col = (event.x / cellWidth).toInt()
            val row = (event.y / cellHeight).toInt()

            if (row >= 1 && row <= 6) {
                val firstDayOfMonth = Calendar.getInstance().apply {
                    timeInMillis = month.timeInMillis
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val daysInMonth = firstDayOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
                val firstDayIndex = firstDayOfMonth.get(Calendar.DAY_OF_WEEK) - 1
                val dayNumber = row * 7 + col - firstDayIndex + 1

                if (dayNumber in 1..daysInMonth) {
                    selectedDay = Calendar.getInstance().apply {
                        timeInMillis = firstDayOfMonth.timeInMillis
                        set(Calendar.DAY_OF_MONTH, dayNumber)
                    }
                    invalidate()
                    onDaySelected?.invoke(selectedDay!!)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}