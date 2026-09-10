package com.dsh.deepseekbalance.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.dsh.deepseekbalance.data.DayPoint
import kotlin.math.max

/** 近 N 天 tokens 用量的柱状图（输入/输出堆叠）。 */
class DailyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var points: List<DayPoint> = emptyList()

    private val promptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4D6BFE") }
    private val completionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#34C759") }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9AA3B2")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val maxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8EAF0")
        textSize = 22f
    }
    private val rect = RectF()

    fun setData(list: List<DayPoint>) {
        points = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return
        val labelH = 30f
        val topPad = 26f
        val chartH = height - labelH - topPad
        if (chartH <= 10f) return

        val maxValue = max(1L, points.maxOf { it.total })
        val slot = width.toFloat() / points.size
        val barW = slot * 0.55f

        for ((index, p) in points.withIndex()) {
            val cx = slot * index + slot / 2f
            val total = p.total
            val h = if (total <= 0) 0f else (total.toFloat() / maxValue) * chartH
            val left = cx - barW / 2f
            val bottom = topPad + chartH
            val top = bottom - h
            val promptH = if (total <= 0) 0f else h * (p.prompt.toFloat() / total)

            if (h > 0f) {
                rect.set(left, top, left + barW, top + promptH)
                canvas.drawRoundRect(rect, 6f, 6f, promptPaint)
                rect.set(left, top + promptH, left + barW, bottom)
                canvas.drawRoundRect(rect, 6f, 6f, completionPaint)
            } else {
                rect.set(left, bottom - 3f, left + barW, bottom)
                canvas.drawRoundRect(rect, 2f, 2f, labelPaint)
            }

            canvas.drawText(
                Fmt.dayLabel(p.day),
                cx,
                height - 8f,
                labelPaint
            )
        }

        canvas.drawText("峰值 " + Fmt.tokens(maxValue), 2f, 20f, maxPaint)
    }
}
