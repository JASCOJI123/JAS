package com.jas.montaj.motion.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.jas.montaj.motion.model.EffectKind
import com.jas.montaj.motion.model.LayerEffect
import com.jas.montaj.motion.model.LayerMask
import com.jas.montaj.motion.model.MaskShape
import com.jas.montaj.motion.model.Vec2
import com.jas.montaj.motion.model.valueAt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object EffectRenderer {
    /** Effektni qatlam rasmiga qo'llaydi. Shake bu yerda emas — u transformga ta'sir qiladi. */
    fun apply(effect: LayerEffect, image: LayerImage, time: Double): LayerImage {
        val amount = effect.amount.valueAt(time)
        val source = image.bitmap
        val pixels = (amount * image.pixelScale).toFloat()
        val result: Bitmap = when (effect.kind) {
            EffectKind.BLUR -> if (pixels > 0.5f) blur(source, pixels) else return image
            EffectKind.GLOW -> {
                if (pixels <= 0.5f) return image
                val halo = blur(source, pixels)
                val out = source.copy(Bitmap.Config.ARGB_8888, true)
                Canvas(out).drawBitmap(halo, 0f, 0f, Paint().apply { blendMode = BlendMode.PLUS })
                out
            }
            EffectKind.CHROMATIC -> {
                if (pixels <= 0.5f) return image
                val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                canvas.drawBitmap(source, pixels, 0f, channelPaint(1f, 0f, 0f, BlendMode.SRC_OVER))
                canvas.drawBitmap(source, 0f, 0f, channelPaint(0f, 1f, 0f, BlendMode.PLUS))
                canvas.drawBitmap(source, -pixels, 0f, channelPaint(0f, 0f, 1f, BlendMode.PLUS))
                out
            }
            EffectKind.SHAKE -> return image
            EffectKind.BRIGHTNESS -> withMatrix(source, ColorMatrix().apply {
                val shift = (amount * 255).toFloat()
                set(floatArrayOf(1f, 0f, 0f, 0f, shift, 0f, 1f, 0f, 0f, shift, 0f, 0f, 1f, 0f, shift, 0f, 0f, 0f, 1f, 0f))
            })
            EffectKind.SATURATION -> withMatrix(source, ColorMatrix().apply { setSaturation(amount.toFloat()) })
            EffectKind.HUE -> withMatrix(source, hueMatrix(amount))
            EffectKind.VIGNETTE -> {
                val out = source.copy(Bitmap.Config.ARGB_8888, true)
                val width = out.width.toFloat()
                val height = out.height.toFloat()
                val radius = max(width, height) * 0.75f
                val edgeAlpha = (amount / 3 * 255).toInt().coerceIn(0, 255)
                val paint = Paint().apply {
                    shader = RadialGradient(
                        width / 2, height / 2, radius,
                        intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(edgeAlpha, 0, 0, 0)),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                    blendMode = BlendMode.SRC_ATOP
                }
                Canvas(out).drawRect(0f, 0f, width, height, paint)
                out
            }
        }
        return LayerImage(result, image.pixelScale)
    }

    /** Shake effekti uchun tasodifiyga o'xshash, lekin har safar bir xil siljish. */
    fun shakeOffset(effects: List<LayerEffect>, time: Double): Vec2 {
        var x = 0.0
        var y = 0.0
        effects.filter { it.enabled && it.kind == EffectKind.SHAKE }.forEach { effect ->
            val amplitude = effect.amount.valueAt(time)
            x += amplitude * (sin(time * 71.3) * 0.6 + sin(time * 23.7 + 1.3) * 0.4)
            y += amplitude * (sin(time * 59.1 + 2.1) * 0.6 + sin(time * 31.3 + 0.7) * 0.4)
        }
        return Vec2(x, y)
    }

    /** Tez taxminiy blur: kichraytirib, qayta kattalashtirish (ikki bosqich). */
    fun blur(source: Bitmap, radius: Float): Bitmap {
        val factor = (radius / 2f).coerceIn(1f, 24f)
        val smallWidth = (source.width / factor).toInt().coerceAtLeast(1)
        val smallHeight = (source.height / factor).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
        val smaller = Bitmap.createScaledBitmap(small, (smallWidth / 2).coerceAtLeast(1), (smallHeight / 2).coerceAtLeast(1), true)
        val back = Bitmap.createScaledBitmap(smaller, smallWidth, smallHeight, true)
        return Bitmap.createScaledBitmap(back, source.width, source.height, true)
    }

    private fun channelPaint(red: Float, green: Float, blue: Float, mode: BlendMode) = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(red, green, blue, 1f) })
        blendMode = mode
    }

    private fun withMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return out
    }

    /** Yorqinlikni saqlagan holda rang tusini aylantirish matritsasi (W3C hueRotate). */
    private fun hueMatrix(degrees: Double): ColorMatrix {
        val angle = Math.toRadians(degrees)
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        return ColorMatrix(
            floatArrayOf(
                0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
                0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
                0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
}

object MaskRenderer {
    fun apply(mask: LayerMask, image: LayerImage): LayerImage {
        val source = image.bitmap
        val width = source.width.toFloat()
        val height = source.height.toFloat()
        val matte = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val rect = RectF(
            (mask.x * width).toFloat(),
            (mask.y * height).toFloat(),
            ((mask.x + mask.width) * width).toFloat(),
            ((mask.y + mask.height) * height).toFloat(),
        )
        val feather = (mask.feather * image.pixelScale).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            if (feather > 0.5f) maskFilter = BlurMaskFilter(feather, BlurMaskFilter.Blur.NORMAL)
        }
        val matteCanvas = Canvas(matte)
        when (mask.shape) {
            MaskShape.RECTANGLE -> matteCanvas.drawRect(rect, paint)
            MaskShape.ELLIPSE -> matteCanvas.drawOval(rect, paint)
        }
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        // Butun kadr o'lchamidagi maska rasmi chiziladi: shunda maskadan tashqari joylar ham to'g'ri yashiriladi.
        Canvas(out).drawBitmap(matte, 0f, 0f, Paint().apply {
            blendMode = if (mask.inverted) BlendMode.DST_OUT else BlendMode.DST_IN
        })
        return LayerImage(out, image.pixelScale)
    }
}
