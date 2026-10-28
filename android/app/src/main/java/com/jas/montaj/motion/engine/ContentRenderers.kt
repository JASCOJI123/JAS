package com.jas.montaj.motion.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.jas.montaj.motion.model.Easing
import com.jas.montaj.motion.model.LayerContent
import com.jas.montaj.motion.model.ShapeKind
import com.jas.montaj.motion.model.TextAnimation
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Qatlamning o'z koordinatalaridagi rasmi.
 * [pixelScale] — bitmap pikseli / mantiqiy piksel (ko'rish oynasida videolar kichikroq olinadi).
 */
class LayerImage(val bitmap: Bitmap, val pixelScale: Float = 1f) {
    val logicalWidth: Float get() = bitmap.width / pixelScale
    val logicalHeight: Float get() = bitmap.height / pixelScale
}

/** Matn joylashuvi: har bir harfning o'rni (harfma-harf animatsiya uchun). */
class TextLayoutData(
    val layout: StaticLayout,
    val paint: TextPaint,
    val width: Int,
    val height: Int,
    val offsetX: Float,
    val padding: Float,
    val glyphs: List<Glyph>,
) {
    class Glyph(val text: String, val x: Float, val baseline: Float, val width: Float, val top: Float, val bottom: Float)
}

object TextRenderer {
    private const val MAX_WIDTH = 960

    fun layout(content: LayerContent.Text): TextLayoutData {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = content.fontSize
            color = content.color
            typeface = Typeface.create(Typeface.DEFAULT, if (content.bold) 900 else 400, false)
            if (content.shadow) setShadowLayer(content.fontSize * 0.12f, 0f, content.fontSize * 0.04f, 0x99000000.toInt())
        }
        val text = content.text.ifEmpty { " " }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, MAX_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        var minLeft = Float.MAX_VALUE
        var maxRight = 0f
        for (line in 0 until layout.lineCount) {
            minLeft = min(minLeft, layout.getLineLeft(line))
            maxRight = max(maxRight, layout.getLineRight(line))
        }
        val padding = content.fontSize * 0.4f
        val offsetX = padding - minLeft
        val glyphs = mutableListOf<TextLayoutData.Glyph>()
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val length = Character.charCount(codePoint)
            val glyph = text.substring(index, index + length)
            if (glyph.isNotBlank()) {
                val line = layout.getLineForOffset(index)
                glyphs += TextLayoutData.Glyph(
                    text = glyph,
                    x = layout.getPrimaryHorizontal(index) + offsetX,
                    baseline = layout.getLineBaseline(line) + padding,
                    width = paint.measureText(glyph),
                    top = layout.getLineTop(line) + padding,
                    bottom = layout.getLineBottom(line) + padding,
                )
            }
            index += length
        }
        return TextLayoutData(
            layout = layout,
            paint = paint,
            width = ceil(maxRight - minLeft + padding * 2).toInt().coerceAtLeast(1),
            height = ceil(layout.height + padding * 2).toInt().coerceAtLeast(1),
            offsetX = offsetX,
            padding = padding,
            glyphs = glyphs,
        )
    }

    /** Animatsiya tugaganmi yoki umuman yo'qmi (unda rasmni keshlash mumkin). */
    fun isStatic(content: LayerContent.Text, localTime: Double): Boolean = when (content.animation) {
        TextAnimation.NONE -> true
        TextAnimation.WAVE -> false
        else -> localTime >= content.animationDuration
    }

    fun render(content: LayerContent.Text, data: TextLayoutData, localTime: Double): Bitmap {
        val bitmap = Bitmap.createBitmap(data.width, data.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (isStatic(content, localTime) && content.animation != TextAnimation.WAVE) {
            canvas.translate(data.offsetX, data.padding)
            data.layout.draw(canvas)
            return bitmap
        }
        val count = data.glyphs.size
        val paint = TextPaint(data.paint)
        data.glyphs.forEachIndexed { index, glyph ->
            val state = glyphState(content, index, count, localTime)
            if (state.alpha <= 0.001f) return@forEachIndexed
            val centerX = glyph.x + glyph.width / 2
            val centerY = (glyph.top + glyph.bottom) / 2
            canvas.save()
            canvas.translate(centerX, centerY + state.offsetY)
            canvas.scale(state.scale, state.scale)
            canvas.translate(-centerX, -centerY)
            paint.alpha = (state.alpha * android.graphics.Color.alpha(content.color)).toInt().coerceIn(0, 255)
            canvas.drawText(glyph.text, glyph.x, glyph.baseline, paint)
            canvas.restore()
        }
        return bitmap
    }

    private class GlyphState(val alpha: Float = 1f, val scale: Float = 1f, val offsetY: Float = 0f)

    private fun glyphState(content: LayerContent.Text, index: Int, count: Int, localTime: Double): GlyphState {
        val total = max(content.animationDuration, 0.05)
        val glyphDuration = total * 0.35
        val stagger = if (count > 1) (total - glyphDuration) / (count - 1) else 0.0
        val progress = ((localTime - index * stagger) / glyphDuration).coerceIn(0.0, 1.0)
        return when (content.animation) {
            TextAnimation.NONE -> GlyphState()
            TextAnimation.TYPEWRITER -> {
                val appearAt = index * total / max(count, 1)
                GlyphState(alpha = if (localTime >= appearAt) 1f else 0f)
            }
            TextAnimation.FADE -> GlyphState(alpha = Easing.EASE_OUT.apply(progress).toFloat())
            TextAnimation.SLIDE_UP -> {
                val eased = Easing.EASE_OUT.apply(progress)
                GlyphState(alpha = eased.toFloat(), offsetY = ((1 - eased) * content.fontSize * 0.8).toFloat())
            }
            TextAnimation.POP -> GlyphState(
                alpha = min(progress * 3, 1.0).toFloat(),
                scale = max(Easing.BACK.apply(progress), 0.0).toFloat(),
            )
            TextAnimation.WAVE -> GlyphState(offsetY = (sin(localTime * 6 - index * 0.5) * content.fontSize * 0.12).toFloat())
        }
    }
}

object ShapeRenderer {
    fun render(shape: LayerContent.Shape): Bitmap {
        val width = shape.width.toInt().coerceAtLeast(1)
        val height = shape.height.toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = shape.strokeWidth / 2
        val rect = RectF(inset, inset, width - inset, height - inset)
        val path = path(shape.kind, rect)
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shape.fill })
        if (shape.strokeWidth > 0) {
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = shape.strokeColor
                style = Paint.Style.STROKE
                strokeWidth = shape.strokeWidth
                strokeJoin = Paint.Join.ROUND
            })
        }
        return bitmap
    }

    private fun path(kind: ShapeKind, rect: RectF): Path = Path().apply {
        when (kind) {
            ShapeKind.RECTANGLE -> addRect(rect, Path.Direction.CW)
            ShapeKind.ROUNDED -> {
                val radius = min(rect.width(), rect.height()) * 0.2f
                addRoundRect(rect, radius, radius, Path.Direction.CW)
            }
            ShapeKind.ELLIPSE -> addOval(rect, Path.Direction.CW)
            ShapeKind.TRIANGLE -> {
                moveTo(rect.centerX(), rect.top)
                lineTo(rect.right, rect.bottom)
                lineTo(rect.left, rect.bottom)
                close()
            }
            ShapeKind.STAR -> {
                val outer = min(rect.width(), rect.height()) / 2
                val inner = outer * 0.45f
                for (point in 0 until 10) {
                    val radius = if (point % 2 == 0) outer else inner
                    val angle = point * Math.PI / 5 - Math.PI / 2
                    val x = rect.centerX() + (cos(angle) * radius).toFloat()
                    val y = rect.centerY() + (sin(angle) * radius).toFloat()
                    if (point == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
    }
}
