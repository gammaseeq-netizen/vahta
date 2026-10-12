package com.vakht.calendar

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient

/** Рисует кольцо-прогресс и иллюстрации для виджета (стиль «жидкого стекла»). */
object Art {

    fun ring(progress: Float): Bitmap {
        val size = 200
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = 16f
        val pad = stroke + 8f
        val rect = RectF(pad, pad, size - pad, size - pad)

        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = Color.argb(70, 255, 255, 255)
        }
        c.drawOval(rect, track)

        val p = progress.coerceIn(0f, 1f)
        if (p > 0.005f) {
            val sweep = 360f * p
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke + 8f
                strokeCap = Paint.Cap.ROUND
                color = Color.argb(110, 90, 220, 255)
                maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawArc(rect, -90f, sweep, false, glow)

            val shader = SweepGradient(
                size / 2f, size / 2f,
                intArrayOf(Color.parseColor("#38BDF8"), Color.parseColor("#E0FAFF")),
                null
            )
            val m = Matrix()
            m.postRotate(-90f, size / 2f, size / 2f)
            shader.setLocalMatrix(m)

            val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                this.shader = shader
            }
            c.drawArc(rect, -90f, sweep, false, arc)
        }
        return bmp
    }

    /** Облако с солнцем (выходные). */
    fun cloud(): Bitmap {
        val w = 480
        val h = 360
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val sx = w * 0.68f
        val sy = h * 0.28f
        val sr = h * 0.32f
        val sun = Paint(Paint.ANTI_ALIAS_FLAG)
        sun.shader = RadialGradient(
            sx, sy, sr,
            intArrayOf(Color.argb(255, 255, 238, 170), Color.argb(130, 255, 214, 120), Color.argb(0, 255, 214, 120)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(sx, sy, sr, sun)

        fun blob(cx: Float, cy: Float, r: Float, a: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(
                cx - r * 0.3f, cy - r * 0.4f, r * 1.4f,
                Color.argb(a, 255, 255, 255), Color.argb(a, 170, 210, 255),
                Shader.TileMode.CLAMP
            )
            p.maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
            c.drawCircle(cx, cy, r, p)
        }

        blob(w * 0.58f, h * 0.50f, h * 0.20f, 225)
        blob(w * 0.78f, h * 0.52f, h * 0.17f, 225)
        blob(w * 0.42f, h * 0.58f, h * 0.15f, 225)
        blob(w * 0.30f, h * 0.76f, h * 0.16f, 245)
        blob(w * 0.50f, h * 0.72f, h * 0.22f, 245)
        blob(w * 0.72f, h * 0.76f, h * 0.18f, 245)
        blob(w * 0.88f, h * 0.80f, h * 0.12f, 245)

        val base = Paint(Paint.ANTI_ALIAS_FLAG)
        base.shader = LinearGradient(
            0f, h * 0.74f, 0f, h * 0.92f,
            Color.argb(245, 235, 247, 255), Color.argb(245, 170, 210, 255),
            Shader.TileMode.CLAMP
        )
        c.drawRoundRect(RectF(w * 0.22f, h * 0.74f, w * 0.92f, h * 0.92f), 40f, 40f, base)
        return bmp
    }

    /** Светящийся портфель (вахта). */
    fun briefcase(): Bitmap {
        val w = 480
        val h = 360
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val body = RectF(w * 0.24f, h * 0.34f, w * 0.84f, h * 0.88f)
        val handle = RectF(w * 0.42f, h * 0.14f, w * 0.66f, h * 0.42f)

        // свечение
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 70, 190, 255)
            maskFilter = BlurMaskFilter(34f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(body, 36f, 36f, glow)
        val glowH = Paint(glow).apply {
            style = Paint.Style.STROKE
            strokeWidth = 22f
        }
        c.drawRoundRect(handle, 24f, 24f, glowH)

        // ручка
        val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 16f
            color = Color.argb(215, 150, 215, 255)
        }
        c.drawRoundRect(handle, 24f, 24f, hp)

        // корпус
        val bp = Paint(Paint.ANTI_ALIAS_FLAG)
        bp.shader = LinearGradient(
            0f, body.top, 0f, body.bottom,
            Color.argb(225, 125, 205, 255), Color.argb(225, 28, 98, 205),
            Shader.TileMode.CLAMP
        )
        c.drawRoundRect(body, 36f, 36f, bp)

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.argb(170, 255, 255, 255)
        }
        c.drawRoundRect(body, 36f, 36f, outline)

        // ремень
        val belt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 10, 40, 110) }
        c.drawRect(RectF(body.left, h * 0.58f, body.right, h * 0.63f), belt)

        // застёжки
        val clasp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 215, 238, 255) }
        c.drawRoundRect(RectF(w * 0.29f, h * 0.55f, w * 0.34f, h * 0.68f), 8f, 8f, clasp)
        c.drawRoundRect(RectF(w * 0.74f, h * 0.55f, w * 0.79f, h * 0.68f), 8f, 8f, clasp)
        c.drawRoundRect(RectF(w * 0.50f, h * 0.55f, w * 0.58f, h * 0.70f), 8f, 8f, clasp)
        return bmp
    }
}
