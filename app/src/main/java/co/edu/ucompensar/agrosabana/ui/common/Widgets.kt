package co.edu.ucompensar.agrosabana.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import co.edu.ucompensar.agrosabana.R
import com.google.android.material.color.MaterialColors

/** Grafica de barras sencilla; la ultima barra se resalta con el color de acento. */
class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // El acento de texto cambia con el tema, asi las barras conservan contraste en modo oscuro.
        color = MaterialColors.getColor(this@BarChartView, R.attr.agroAccentText)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.agro_amber)
    }
    private val rect = RectF()
    private val radius = 4 * resources.displayMetrics.density

    var values: List<Int> = listOf(3, 5, 4, 7, 6, 8, 9)
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val max = values.max().coerceAtLeast(1).toFloat()
        val slot = width.toFloat() / values.size
        val barWidth = slot * 0.62f
        values.forEachIndexed { index, value ->
            val left = index * slot + (slot - barWidth) / 2
            rect.set(left, height * (1 - value / max), left + barWidth, height.toFloat())
            canvas.drawRoundRect(rect, radius, radius, if (index == values.lastIndex) accentPaint else barPaint)
        }
    }
}

/** Indicador de pasos usado en el checkout y en el estado de los pedidos. */
class StepperView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val activeColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private val idleColor = ContextCompat.getColor(context, R.color.stroke_soft)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2 * density }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12 * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val textColor = ContextCompat.getColor(context, R.color.text_primary)
    private val textIdleColor = ContextCompat.getColor(context, R.color.text_secondary)

    private var steps: List<String> = emptyList()
    private var current = 0

    fun setSteps(labels: List<String>, currentIndex: Int) {
        steps = labels
        current = currentIndex
        contentDescription = labels.getOrNull(currentIndex)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (44 * density).toInt().coerceAtLeast((24 * density + textPaint.textSize * 1.6f).toInt())
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (steps.isEmpty()) return
        val slot = width.toFloat() / steps.size
        val y = 10 * density
        val dot = 7 * density
        for (i in 0 until steps.lastIndex) {
            paint.color = if (i < current) activeColor else idleColor
            canvas.drawLine(slot * i + slot / 2, y, slot * (i + 1) + slot / 2, y, paint)
        }
        steps.forEachIndexed { i, label ->
            val x = slot * i + slot / 2
            paint.color = if (i <= current) activeColor else idleColor
            canvas.drawCircle(x, y, dot, paint)
            textPaint.color = if (i == current) textColor else textIdleColor
            textPaint.isFakeBoldText = i == current
            canvas.drawText(label, x, height - 6 * density, textPaint)
        }
    }
}
