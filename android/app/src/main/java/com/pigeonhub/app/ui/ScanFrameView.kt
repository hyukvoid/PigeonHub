package com.pigeonhub.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * BETA-001A scan guide: dims everything outside a centered square and draws
 * rounded corner brackets so the user knows exactly where the QR belongs.
 * White brackets over a translucent dark scrim read clearly in both light
 * and dark mode; the camera preview inside the frame stays untouched.
 */
class ScanFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59000000.toInt() }
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = density * 4
        strokeCap = Paint.Cap.ROUND
    }
    private val frame = RectF()
    private val scrim = Path()
    private val cornerRadius = density * 16
    private val bracketLength = density * 26

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        if (width == 0 || height == 0) return
        // Responsive square: ~74% of the shorter side, capped at 280dp.
        // (Parentheses matter: 0.74f.coerceAtMost would coerce the constant.)
        val side = (minOf(width, height) * 0.74f)
            .coerceAtMost(density * 280)
            .coerceAtLeast(density * 240)
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        frame.set(left, top, left + side, top + side)
    }

    override fun onDraw(canvas: Canvas) {
        if (frame.isEmpty) return
        scrim.reset()
        scrim.fillType = Path.FillType.EVEN_ODD
        scrim.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        scrim.addRoundRect(frame, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.drawPath(scrim, dimPaint)
        drawBrackets(canvas)
    }

    private fun drawBrackets(canvas: Canvas) {
        val left = frame.left
        val top = frame.top
        val right = frame.right
        val bottom = frame.bottom
        val r = cornerRadius
        val length = bracketLength

        // Top-left: arc from the left edge to the top edge, then straight runs.
        canvas.drawArc(left, top, left + 2 * r, top + 2 * r, 180f, 90f, false, bracketPaint)
        canvas.drawLine(left + r, top, left + r + length, top, bracketPaint)
        canvas.drawLine(left, top + r, left, top + r + length, bracketPaint)
        // Top-right
        canvas.drawArc(right - 2 * r, top, right, top + 2 * r, 270f, 90f, false, bracketPaint)
        canvas.drawLine(right - r - length, top, right - r, top, bracketPaint)
        canvas.drawLine(right, top + r, right, top + r + length, bracketPaint)
        // Bottom-right
        canvas.drawArc(right - 2 * r, bottom - 2 * r, right, bottom, 0f, 90f, false, bracketPaint)
        canvas.drawLine(right, bottom - r - length, right, bottom - r, bracketPaint)
        canvas.drawLine(right - r - length, bottom, right - r, bottom, bracketPaint)
        // Bottom-left
        canvas.drawArc(left, bottom - 2 * r, left + 2 * r, bottom, 90f, 90f, false, bracketPaint)
        canvas.drawLine(left, bottom - r - length, left, bottom - r, bracketPaint)
        canvas.drawLine(left + r - length, bottom, left + r, bottom, bracketPaint)
    }
}
