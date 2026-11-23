package com.thiefalertsequre.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Live dashboard shield. It reflects the persisted protection mode; it never
 * changes protection state or starts/stops the monitor itself.
 */
class ProtectionShieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var protectionMode: ProtectionMode = ProtectionMode.DISARMED
    private var monitorReady: Boolean = false

    fun update(mode: ProtectionMode, monitorActive: Boolean) {
        protectionMode = mode
        monitorReady = monitorActive
        contentDescription = when (mode) {
            ProtectionMode.DISARMED -> "Protection ready. Not armed."
            ProtectionMode.ARMED -> if (monitorActive) "Protected. Charger protection armed." else "Protection armed, but monitor is not confirmed running."
            ProtectionMode.COUNTDOWN -> "Warning. Charger removal countdown is active."
            ProtectionMode.ALARM_TRIGGERED -> "Danger. Theft alarm is triggered."
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        val save = canvas.save()
        canvas.translate((width - side) / 2f, (height - side) / 2f)
        canvas.scale(side, side)

        val color = when (protectionMode) {
            ProtectionMode.DISARMED -> Color.rgb(139, 157, 178)
            ProtectionMode.ARMED -> if (monitorReady) Color.rgb(55, 213, 132) else Color.rgb(139, 157, 178)
            ProtectionMode.COUNTDOWN -> Color.rgb(255, 190, 55)
            ProtectionMode.ALARM_TRIGGERED -> Color.rgb(255, 67, 82)
        }
        val fill = when (protectionMode) {
            ProtectionMode.DISARMED -> Color.rgb(25, 43, 62)
            ProtectionMode.ARMED -> if (monitorReady) Color.rgb(13, 75, 53) else Color.rgb(25, 43, 62)
            ProtectionMode.COUNTDOWN -> Color.rgb(88, 61, 15)
            ProtectionMode.ALARM_TRIGGERED -> Color.rgb(91, 24, 34)
        }

        // Subtle halo and a bold shield silhouette sized for the dashboard.
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = 28
        canvas.drawCircle(0.5f, 0.49f, 0.48f, paint)
        paint.alpha = 255

        val shield = Path().apply {
            moveTo(0.5f, 0.055f)
            lineTo(0.87f, 0.19f)
            lineTo(0.84f, 0.57f)
            cubicTo(0.82f, 0.74f, 0.67f, 0.88f, 0.5f, 0.955f)
            cubicTo(0.33f, 0.88f, 0.18f, 0.74f, 0.16f, 0.57f)
            lineTo(0.13f, 0.19f)
            close()
        }
        paint.color = fill
        canvas.drawPath(shield, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.035f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color
        canvas.drawPath(shield, paint)
        paint.style = Paint.Style.FILL

        when (protectionMode) {
            ProtectionMode.ARMED -> if (monitorReady) drawCheck(canvas, color) else drawDot(canvas, color)
            ProtectionMode.COUNTDOWN -> drawCountdown(canvas, color)
            ProtectionMode.ALARM_TRIGGERED -> drawThief(canvas, color)
            ProtectionMode.DISARMED -> drawDot(canvas, color)
        }
        canvas.restoreToCount(save)
    }

    private fun drawCheck(canvas: Canvas, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.075f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val check = Path().apply {
            moveTo(0.32f, 0.51f)
            lineTo(0.45f, 0.64f)
            lineTo(0.70f, 0.37f)
        }
        canvas.drawPath(check, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawCountdown(canvas: Canvas, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.045f
        canvas.drawCircle(0.5f, 0.51f, 0.19f, paint)
        paint.strokeWidth = 0.055f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(0.5f, 0.51f, 0.5f, 0.385f, paint)
        canvas.drawLine(0.5f, 0.51f, 0.61f, 0.56f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawThief(canvas: Canvas, color: Int) {
        // Hooded face and eye-mask silhouette: alarm-only visual.
        paint.color = color
        val hood = Path().apply {
            moveTo(0.27f, 0.65f)
            cubicTo(0.23f, 0.34f, 0.37f, 0.22f, 0.5f, 0.22f)
            cubicTo(0.67f, 0.22f, 0.79f, 0.36f, 0.73f, 0.65f)
            lineTo(0.66f, 0.77f)
            lineTo(0.34f, 0.77f)
            close()
        }
        canvas.drawPath(hood, paint)
        paint.color = Color.rgb(255, 225, 218)
        canvas.drawOval(0.37f, 0.36f, 0.63f, 0.68f, paint)
        paint.color = Color.rgb(42, 18, 23)
        val mask = Path().apply {
            moveTo(0.36f, 0.47f)
            lineTo(0.64f, 0.47f)
            lineTo(0.61f, 0.56f)
            lineTo(0.50f, 0.59f)
            lineTo(0.39f, 0.56f)
            close()
        }
        canvas.drawPath(mask, paint)
        paint.color = color
        canvas.drawCircle(0.44f, 0.51f, 0.018f, paint)
        canvas.drawCircle(0.56f, 0.51f, 0.018f, paint)
    }

    private fun drawDot(canvas: Canvas, color: Int) {
        paint.color = color
        canvas.drawCircle(0.5f, 0.51f, 0.075f, paint)
    }
}
