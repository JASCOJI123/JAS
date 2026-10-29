package com.jas.montaj.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.jas.montaj.montaj.TextOverlayItem
import com.jas.montaj.motion.Easing
import com.jas.montaj.motion.LayerContent
import com.jas.montaj.motion.TextAnimation
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Matnni chizish: o'lchamlar piksel (density = 1), shuning uchun
 * ko'rish oynasi va eksportda bir xil chiqadi.
 */
class TextRenderer(fontFamilyResolver: FontFamily.Resolver) {
    private val density = Density(1f, 1f)
    private val measurer = TextMeasurer(fontFamilyResolver, density, LayoutDirection.Ltr, cacheSize = 64)

    // region Montaj matnlari (subtitr)

    fun drawOverlay(canvas: Canvas, overlay: TextOverlayItem, width: Float, height: Float) {
        val text = overlay.text.trim()
        if (text.isEmpty()) return
        val style = TextStyle(
            color = Color(overlay.color),
            fontSize = overlay.fontSize.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            shadow = if (overlay.hasBackground) null else Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 3f), 10f),
        )
        val maxWidth = (width * 0.86f).toInt()
        val layout = measurer.measure(text, style, constraints = Constraints(maxWidth = maxWidth))
        val bounds = inkBounds(layout)
        val left = (width - layout.size.width) / 2f
        val top = (height * overlay.position.anchor - layout.size.height / 2f).coerceIn(0f, max(height - layout.size.height, 0f))

        if (overlay.hasBackground) {
            val padding = overlay.fontSize * 0.35f
            val boxLeft = left + bounds.first - padding
            val boxRight = left + bounds.second + padding
            canvas.drawRoundRect(
                boxLeft, top - padding * 0.6f, boxRight, top + layout.size.height + padding * 0.6f,
                padding, padding, Paint().apply { color = Color.Black.copy(alpha = 0.55f) },
            )
        }
        draw(canvas, Size(width, height)) { drawText(layout, topLeft = Offset(left, top)) }
    }

    // endregion

    // region Motion matn qatlami

    class Layout(
        val result: TextLayoutResult,
        val style: TextStyle,
        val width: Int,
        val height: Int,
        val offsetX: Float,
        val padding: Float,
        val glyphs: List<Glyph>,
    )

    class Glyph(val text: String, val left: Float, val top: Float, val width: Float, val height: Float, val baseline: Float)

    fun layout(content: LayerContent.Text): Layout {
        val style = TextStyle(
            color = Color(content.color),
            fontSize = content.fontSize.sp,
            fontWeight = if (content.bold) FontWeight.Black else FontWeight.Normal,
            textAlign = TextAlign.Center,
            shadow = if (content.shadow) Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, content.fontSize * 0.04f), content.fontSize * 0.12f) else null,
        )
        val text = content.text.ifEmpty { " " }
        val result = measurer.measure(text, style, constraints = Constraints(maxWidth = 960))
        val (minLeft, maxRight) = inkBounds(result)
        val padding = content.fontSize * 0.4f
        val offsetX = padding - minLeft
        val glyphs = mutableListOf<Glyph>()
        var index = 0
        while (index < text.length) {
            val length = if (text[index].isHighSurrogate() && index + 1 < text.length) 2 else 1
            val glyph = text.substring(index, index + length)
            if (glyph.isNotBlank()) {
                val box = result.getBoundingBox(index)
                val line = result.getLineForOffset(index)
                glyphs += Glyph(
                    text = glyph,
                    left = box.left + offsetX,
                    top = box.top + padding,
                    width = box.width,
                    height = box.height,
                    baseline = result.getLineBaseline(line) + padding,
                )
            }
            index += length
        }
        return Layout(
            result = result,
            style = style,
            width = ceil(maxRight - minLeft + padding * 2).toInt().coerceAtLeast(1),
            height = ceil(result.size.height + padding * 2).toInt().coerceAtLeast(1),
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

    fun render(content: LayerContent.Text, layout: Layout, localTime: Double): ImageBitmap {
        val image = Graphics.blank(layout.width, layout.height)
        val canvas = Canvas(image)
        val size = Size(layout.width.toFloat(), layout.height.toFloat())
        if (isStatic(content, localTime) && content.animation != TextAnimation.WAVE) {
            draw(canvas, size) { drawText(layout.result, topLeft = Offset(layout.offsetX, layout.padding)) }
            return image
        }
        val count = layout.glyphs.size
        draw(canvas, size) {
            layout.glyphs.forEachIndexed { index, glyph ->
                val state = glyphState(content, index, count, localTime)
                if (state.alpha <= 0.001f) return@forEachIndexed
                val glyphLayout = measurer.measure(glyph.text, layout.style.copy(textAlign = TextAlign.Start))
                val pivot = Offset(glyph.left + glyph.width / 2, glyph.top + glyph.height / 2 + state.offsetY)
                translate(0f, state.offsetY) {
                    scale(state.scale, state.scale, pivot = pivot) {
                        drawText(
                            glyphLayout,
                            topLeft = Offset(glyph.left, glyph.baseline - glyphLayout.firstBaseline),
                            alpha = state.alpha,
                        )
                    }
                }
            }
        }
        return image
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

    // endregion

    private fun inkBounds(layout: TextLayoutResult): Pair<Float, Float> {
        var minLeft = Float.MAX_VALUE
        var maxRight = 0f
        for (line in 0 until layout.lineCount) {
            minLeft = min(minLeft, layout.getLineLeft(line))
            maxRight = max(maxRight, layout.getLineRight(line))
        }
        if (minLeft == Float.MAX_VALUE) minLeft = 0f
        return minLeft to maxRight
    }

    private fun draw(canvas: Canvas, size: Size, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, size, block)
    }
}
