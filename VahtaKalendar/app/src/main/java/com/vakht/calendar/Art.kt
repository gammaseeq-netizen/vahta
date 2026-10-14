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

    /** Облако с солнцем (отдых). Всё нарисовано внутри холста, чтобы не было обрезанных краёв. */
    fun cloud(): Bitmap {
        val s = 360
        val f = s.toFloat()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // солнечное свечение
        val sx = f * 0.68f
        val sy = f * 0.30f
        val sr = f * 0.28f
        val sun = Paint(Paint.ANTI_ALIAS_FLAG)
        sun.shader = RadialGradient(
            sx, sy, sr,
            intArrayOf(
                Color.argb(255, 255, 240, 180),
                Color.argb(150, 255, 214, 120),
                Color.argb(0, 255, 214, 120)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(sx, sy, sr, sun)

        fun drawCloud(p: Paint, dy: Float) {
            c.drawCircle(f * 0.32f, f * 0.60f + dy, f * 0.14f, p)
            c.drawCircle(f * 0.50f, f * 0.50f + dy, f * 0.21f, p)
            c.drawCircle(f * 0.70f, f * 0.58f + dy, f * 0.15f, p)
            c.drawRoundRect(
                RectF(f * 0.14f, f * 0.58f + dy, f * 0.86f, f * 0.80f + dy),
                f * 0.11f, f * 0.11f, p
            )
        }

        // мягкая тень
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 20, 70, 160)
            maskFilter = BlurMaskFilter(f * 0.03f, BlurMaskFilter.Blur.NORMAL)
        }
        drawCloud(shadow, f * 0.03f)

        // тело облака: один общий градиент на все части, поэтому швов нет
        val body = Paint(Paint.ANTI_ALIAS_FLAG)
        body.shader = LinearGradient(
            0f, f * 0.29f, 0f, f * 0.80f,
            Color.argb(255, 255, 255, 255),
            Color.argb(255, 196, 224, 255),
            Shader.TileMode.CLAMP
        )
        drawCloud(body, 0f)
        return bmp
    }

    /** Светящийся портфель (вахта). */
    fun briefcase(): Bitmap {
        val s = 360
        val f = s.toFloat()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val body = RectF(f * 0.14f, f * 0.38f, f * 0.86f, f * 0.88f)
        val handle = RectF(f * 0.35f, f * 0.16f, f * 0.65f, f * 0.44f)

        // свечение
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 70, 190, 255)
            maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(body, 34f, 34f, glow)
        val glowH = Paint(glow).apply {
            style = Paint.Style.STROKE
            strokeWidth = 20f
        }
        c.drawRoundRect(handle, 22f, 22f, glowH)

        // ручка
        val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 15f
            color = Color.argb(215, 150, 215, 255)
        }
        c.drawRoundRect(handle, 22f, 22f, hp)

        // корпус
        val bp = Paint(Paint.ANTI_ALIAS_FLAG)
        bp.shader = LinearGradient(
            0f, body.top, 0f, body.bottom,
            Color.argb(225, 125, 205, 255), Color.argb(225, 28, 98, 205),
            Shader.TileMode.CLAMP
        )
        c.drawRoundRect(body, 34f, 34f, bp)

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.argb(170, 255, 255, 255)
        }
        c.drawRoundRect(body, 34f, 34f, outline)

        // ремень
        val belt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 10, 40, 110) }
        c.drawRect(RectF(body.left, f * 0.60f, body.right, f * 0.65f), belt)

        // застёжки
        val clasp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 215, 238, 255) }
        c.drawRoundRect(RectF(f * 0.21f, f * 0.57f, f * 0.27f, f * 0.70f), 8f, 8f, clasp)
        c.drawRoundRect(RectF(f * 0.73f, f * 0.57f, f * 0.79f, f * 0.70f), 8f, 8f, clasp)
        c.drawRoundRect(RectF(f * 0.46f, f * 0.57f, f * 0.54f, f * 0.72f), 8f, 8f, clasp)
        return bmp
    }
}
