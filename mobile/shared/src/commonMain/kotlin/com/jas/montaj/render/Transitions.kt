package com.jas.montaj.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.jas.montaj.montaj.ColorMatrices
import com.jas.montaj.montaj.TransitionKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** Ikki kadr orasidagi o'tish effektlari ([progress] 0 → [from], 1 → [to]). */
object Transitions {
    fun draw(canvas: Canvas, kind: TransitionKind, from: ImageBitmap, to: ImageBitmap, progress: Float, width: Float, height: Float) {
        val p = progress.coerceIn(0f, 1f)
        when (kind) {
            TransitionKind.NONE -> Graphics.drawImageFull(canvas, if (p < 0.5f) from else to)
            TransitionKind.FADE -> {
                Graphics.drawImageFull(canvas, from)
                Graphics.drawImageFull(canvas, to, Graphics.smoothPaint().apply { alpha = p })
            }
            TransitionKind.BLACK -> {
                if (p < 0.5f) {
                    Graphics.drawImageFull(canvas, from, Graphics.smoothPaint().apply { alpha = 1 - p * 2 })
                } else {
                    Graphics.drawImageFull(canvas, to, Graphics.smoothPaint().apply { alpha = p * 2 - 1 })
                }
            }
            TransitionKind.SLIDE -> {
                Graphics.drawScaled(canvas, from, -p * width, 0f, width, height)
                Graphics.drawScaled(canvas, to, (1 - p) * width, 0f, width, height)
            }
            TransitionKind.PUSH_UP -> {
                Graphics.drawScaled(canvas, from, 0f, -p * height, width, height)
                Graphics.drawScaled(canvas, to, 0f, (1 - p) * height, width, height)
            }
            TransitionKind.ZOOM -> {
                drawZoomed(canvas, from, 1f + p * 0.6f, 1f - p, width, height)
                drawZoomed(canvas, to, 1.3f - p * 0.3f, p, width, height)
            }
            TransitionKind.WIPE -> {
                Graphics.drawImageFull(canvas, from)
                canvas.save()
                canvas.clipRect(0f, 0f, p * width, height)
                Graphics.drawImageFull(canvas, to)
                canvas.restore()
            }
            TransitionKind.GLITCH -> drawGlitch(canvas, if (p < 0.5f) from else to, p, width, height)
        }
    }

    private fun drawZoomed(canvas: Canvas, image: ImageBitmap, scale: Float, alpha: Float, width: Float, height: Float) {
        val scaledWidth = width * scale
        val scaledHeight = height * scale
        Graphics.drawScaled(
            canvas, image, (width - scaledWidth) / 2, (height - scaledHeight) / 2, scaledWidth, scaledHeight,
            Graphics.smoothPaint().apply { this.alpha = alpha.coerceIn(0f, 1f) },
        )
    }

    /** RGB ajralishi va gorizontal "uzilgan" bo'laklar; o'rtada eng kuchli. */
    private fun drawGlitch(canvas: Canvas, image: ImageBitmap, p: Float, width: Float, height: Float) {
        val strength = sin(p * PI).toFloat()
        val shift = strength * 40f
        Graphics.drawImageFull(canvas, image)
        val red = Graphics.smoothPaint().apply {
            colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(ColorMatrices.scale(1f, 0f, 0f)))
            blendMode = BlendMode.Plus
            alpha = strength * 0.6f
        }
        val blue = Graphics.smoothPaint().apply {
            colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(ColorMatrices.scale(0f, 0f, 1f)))
            blendMode = BlendMode.Plus
            alpha = strength * 0.6f
        }
        canvas.drawImage(image, Offset(shift, 0f), red)
        canvas.drawImage(image, Offset(-shift, 0f), blue)

        // Tasodifiyga o'xshash, lekin har kadrda bir xil bo'laklar (progressdan hisoblanadi).
        val bands = 7
        val seed = (p * 12).toInt()
        for (band in 0 until bands) {
            val noise = sin((band * 12.9898 + seed * 78.233) * 43758.5453).let { it - kotlin.math.floor(it) }
            if (noise < 0.45) continue
            val top = (height * band / bands).roundToInt()
            val bandHeight = (height / bands * (0.3f + noise.toFloat() * 0.5f)).roundToInt().coerceAtLeast(1)
            val offset = ((noise - 0.7) * 2 * shift * 3).roundToInt()
            val srcTop = (top * image.height / height).roundToInt()
            val srcHeight = (bandHeight * image.height / height).roundToInt().coerceIn(1, image.height - srcTop.coerceAtMost(image.height - 1))
            if (abs(offset) < 1) continue
            canvas.drawImageRect(
                image,
                IntOffset(0, srcTop.coerceIn(0, image.height - 1)),
                IntSize(image.width, srcHeight),
                IntOffset(offset, top),
                IntSize(width.roundToInt(), bandHeight),
                Graphics.smoothPaint(),
            )
        }
    }
}
