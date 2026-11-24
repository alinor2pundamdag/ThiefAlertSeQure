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
    private val shieldPath = Path()
    private val checkPath = Path()
    private val hoodPath = Path()
    private val maskPath = Path()
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

        // The shield silhouette keeps one stable brand color in every state;
        // the inner state glyph and adjacent status text carry the live state.
        val shieldColor = Color.rgb(64, 177, 224)
        val statusColor = when (protectionMode) {
            ProtectionMode.DISARMED -> Color.rgb(139, 157, 178)
            ProtectionMode.ARMED -> if (monitorReady) Color.rgb(55, 213, 132) else Color.rgb(139, 157, 178)
            ProtectionMode.COUNTDOWN -> Color.rgb(255, 190, 55)
            ProtectionMode.ALARM_TRIGGERED -> Color.rgb(255, 67, 82)
        }
        val fill = Color.rgb(25, 43, 62)

        // Subtle halo and a bold shield silhouette sized for the dashboard.
        paint.style = Paint.Style.FILL
        paint.color = shieldColor
        paint.alpha = 28
        canvas.drawCircle(0.5f, 0.49f, 0.48f, paint)
        paint.alpha = 255

        shieldPath.reset()
        shieldPath.moveTo(0.5f, 0.055f)
        shieldPath.lineTo(0.87f, 0.19f)
        shieldPath.lineTo(0.84f, 0.57f)
        shieldPath.cubicTo(0.82f, 0.74f, 0.67f, 0.88f, 0.5f, 0.955f)
        shieldPath.cubicTo(0.33f, 0.88f, 0.18f, 0.74f, 0.16f, 0.57f)
        shieldPath.lineTo(0.13f, 0.19f)
        shieldPath.close()
        paint.color = fill
        canvas.drawPath(shieldPath, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.035f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = shieldColor
        canvas.drawPath(shieldPath, paint)
        paint.style = Paint.Style.FILL

        when (protectionMode) {
            ProtectionMode.ARMED -> if (monitorReady) drawCheck(canvas, statusColor) else drawDot(canvas, statusColor)
            ProtectionMode.COUNTDOWN -> drawCountdown(canvas, statusColor)
            ProtectionMode.ALARM_TRIGGERED -> drawThief(canvas, statusColor)
            ProtectionMode.DISARMED -> drawDot(canvas, statusColor)
        }
        canvas.restoreToCount(save)
    }

    private fun drawCheck(canvas: Canvas, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.075f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        checkPath.reset()
        checkPath.moveTo(0.32f, 0.51f)
        checkPath.lineTo(0.45f, 0.64f)
        checkPath.lineTo(0.70f, 0.37f)
        canvas.drawPath(checkPath, paint)
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
        hoodPath.reset()
        hoodPath.moveTo(0.27f, 0.65f)
        hoodPath.cubicTo(0.23f, 0.34f, 0.37f, 0.22f, 0.5f, 0.22f)
        hoodPath.cubicTo(0.67f, 0.22f, 0.79f, 0.36f, 0.73f, 0.65f)
        hoodPath.lineTo(0.66f, 0.77f)
        hoodPath.lineTo(0.34f, 0.77f)
        hoodPath.close()
        canvas.drawPath(hoodPath, paint)
        paint.color = Color.rgb(255, 225, 218)
        canvas.drawOval(0.37f, 0.36f, 0.63f, 0.68f, paint)
        paint.color = Color.rgb(42, 18, 23)
        maskPath.reset()
        maskPath.moveTo(0.36f, 0.47f)
        maskPath.lineTo(0.64f, 0.47f)
        maskPath.lineTo(0.61f, 0.56f)
        maskPath.lineTo(0.50f, 0.59f)
        maskPath.lineTo(0.39f, 0.56f)
        maskPath.close()
        canvas.drawPath(maskPath, paint)
        paint.color = color
        canvas.drawCircle(0.44f, 0.51f, 0.018f, paint)
        canvas.drawCircle(0.56f, 0.51f, 0.018f, paint)
    }

    private fun drawDot(canvas: Canvas, color: Int) {
        paint.color = color
        canvas.drawCircle(0.5f, 0.51f, 0.075f, paint)
    }
}
