package com.jas.montaj.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.StrokeJoin
import com.jas.montaj.montaj.ColorMatrices
import com.jas.montaj.motion.EffectKind
import com.jas.montaj.motion.LayerBlend
import com.jas.montaj.motion.LayerContent
import com.jas.montaj.motion.LayerEffect
import com.jas.montaj.motion.LayerMask
import com.jas.montaj.motion.MaskShape
import com.jas.montaj.motion.ShapeKind
import com.jas.montaj.motion.Vec2
import com.jas.montaj.motion.valueAt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Qatlamning o'z koordinatalaridagi rasmi.
 * [pixelScale] — rasm pikseli / mantiqiy piksel (ko'rish oynasida videolar kichikroq olinadi).
 */
class LayerImage(val image: ImageBitmap, val pixelScale: Float = 1f) {
    val logicalWidth: Float get() = image.width / pixelScale
    val logicalHeight: Float get() = image.height / pixelScale
}

fun LayerBlend.composeMode(): BlendMode = when (this) {
    LayerBlend.NORMAL -> BlendMode.SrcOver
    LayerBlend.ADD -> BlendMode.Plus
    LayerBlend.SCREEN -> BlendMode.Screen
    LayerBlend.MULTIPLY -> BlendMode.Multiply
    LayerBlend.OVERLAY -> BlendMode.Overlay
    LayerBlend.SOFT_LIGHT -> BlendMode.Softlight
    LayerBlend.LIGHTEN -> BlendMode.Lighten
    LayerBlend.DARKEN -> BlendMode.Darken
    LayerBlend.DIFFERENCE -> BlendMode.Difference
}

object EffectRenderer {
    /** Effektni qatlam rasmiga qo'llaydi. Shake bu yerda emas — u transformga ta'sir qiladi. */
    fun apply(effect: LayerEffect, layer: LayerImage, time: Double): LayerImage {
        val amount = effect.amount.valueAt(time)
        val source = layer.image
        val pixels = (amount * layer.pixelScale).toFloat()
        val result: ImageBitmap = when (effect.kind) {
            EffectKind.BLUR -> if (pixels > 0.5f) Graphics.blur(source, pixels) else return layer
            EffectKind.GLOW -> {
                if (pixels <= 0.5f) return layer
                val out = Graphics.copy(source)
                Graphics.composite(out, Graphics.blur(source, pixels), BlendMode.Plus)
                out
            }
            EffectKind.CHROMATIC -> {
                if (pixels <= 0.5f) return layer
                val out = Graphics.blank(source.width, source.height)
                Graphics.composite(out, source, BlendMode.SrcOver, dx = pixels, matrix = ColorMatrices.scale(1f, 0f, 0f))
                Graphics.composite(out, source, BlendMode.Plus, matrix = ColorMatrices.scale(0f, 1f, 0f))
                Graphics.composite(out, source, BlendMode.Plus, dx = -pixels, matrix = ColorMatrices.scale(0f, 0f, 1f))
                out
            }
            EffectKind.SHAKE -> return layer
            EffectKind.BRIGHTNESS -> Graphics.withColorMatrix(source, ColorMatrices.brightness((amount * 255).toFloat()))
            EffectKind.SATURATION -> Graphics.withColorMatrix(source, ColorMatrices.saturation(amount.toFloat()))
            EffectKind.HUE -> Graphics.withColorMatrix(source, hueMatrix(amount))
            EffectKind.VIGNETTE -> {
                val out = Graphics.copy(source)
                val width = out.width.toFloat()
                val height = out.height.toFloat()
                val edgeAlpha = (amount / 3).coerceIn(0.0, 1.0).toFloat()
                val paint = Paint().apply {
                    shader = RadialGradientShader(
                        center = Offset(width / 2, height / 2),
                        radius = max(width, height) * 0.75f,
                        colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = edgeAlpha)),
                        colorStops = listOf(0f, 0.45f, 1f),
                    )
                    blendMode = BlendMode.SrcAtop
                }
                Canvas(out).drawRect(0f, 0f, width, height, paint)
                out
            }
        }
        return LayerImage(result, layer.pixelScale)
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

    /** Yorqinlikni saqlagan holda rang tusini aylantirish (W3C hueRotate). */
    private fun hueMatrix(degrees: Double): FloatArray {
        val angle = degrees * PI / 180
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        return floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}

object MaskRenderer {
    fun apply(mask: LayerMask, layer: LayerImage): LayerImage {
        val source = layer.image
        val width = source.width.toFloat()
        val height = source.height.toFloat()
        var matte = Graphics.blank(source.width, source.height)
        val rect = Rect(
            (mask.x * width).toFloat(),
            (mask.y * height).toFloat(),
            ((mask.x + mask.width) * width).toFloat(),
            ((mask.y + mask.height) * height).toFloat(),
        )
        val paint = Paint().apply {
            color = Color.Black
            isAntiAlias = true
        }
        val canvas = Canvas(matte)
        when (mask.shape) {
            MaskShape.RECTANGLE -> canvas.drawRect(rect, paint)
            MaskShape.ELLIPSE -> canvas.drawOval(rect, paint)
        }
        val feather = (mask.feather * layer.pixelScale).toFloat()
        if (feather > 0.5f) matte = Graphics.blur(matte, feather)

        val out = Graphics.copy(source)
        // Butun kadr o'lchamidagi maska chiziladi: maskadan tashqari joylar ham to'g'ri yashiriladi.
        Graphics.composite(out, matte, if (mask.inverted) BlendMode.DstOut else BlendMode.DstIn)
        return LayerImage(out, layer.pixelScale)
    }
}

object ShapeRenderer {
    fun render(shape: LayerContent.Shape): ImageBitmap {
        val width = shape.width.toInt().coerceAtLeast(1)
        val height = shape.height.toInt().coerceAtLeast(1)
        val image = Graphics.blank(width, height)
        val canvas = Canvas(image)
        val inset = shape.strokeWidth / 2
        val rect = Rect(inset, inset, width - inset, height - inset)
        val path = path(shape.kind, rect)
        canvas.drawPath(path, Paint().apply {
            color = Color(shape.fill)
            isAntiAlias = true
        })
        if (shape.strokeWidth > 0) {
            canvas.drawPath(path, Paint().apply {
                color = Color(shape.strokeColor)
                style = PaintingStyle.Stroke
                strokeWidth = shape.strokeWidth
                strokeJoin = StrokeJoin.Round
                isAntiAlias = true
            })
        }
        return image
    }

    private fun path(kind: ShapeKind, rect: Rect): Path = Path().apply {
        when (kind) {
            ShapeKind.RECTANGLE -> addRect(rect)
            ShapeKind.ROUNDED -> {
                val radius = min(rect.width, rect.height) * 0.2f
                addRoundRect(RoundRect(rect, CornerRadius(radius, radius)))
            }
            ShapeKind.ELLIPSE -> addOval(rect)
            ShapeKind.TRIANGLE -> {
                moveTo(rect.center.x, rect.top)
                lineTo(rect.right, rect.bottom)
                lineTo(rect.left, rect.bottom)
                close()
            }
            ShapeKind.STAR -> {
                val outer = min(rect.width, rect.height) / 2
                val inner = outer * 0.45f
                for (point in 0 until 10) {
                    val radius = if (point % 2 == 0) outer else inner
                    val angle = point * PI / 5 - PI / 2
                    val x = rect.center.x + (cos(angle) * radius).toFloat()
                    val y = rect.center.y + (sin(angle) * radius).toFloat()
                    if (point == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
    }
}
