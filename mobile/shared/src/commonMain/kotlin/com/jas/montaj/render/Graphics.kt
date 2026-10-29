package com.jas.montaj.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Rasm ustida ishlash uchun umumiy yordamchilar (Compose graphics — Android'da Canvas, iOS'da Skia).
 */
object Graphics {
    fun blank(width: Int, height: Int): ImageBitmap = ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))

    fun argb(color: Int): Color = Color(color)

    fun drawScaled(canvas: Canvas, image: ImageBitmap, x: Float, y: Float, width: Float, height: Float, paint: Paint = smoothPaint()) {
        canvas.drawImageRect(
            image,
            IntOffset.Zero,
            IntSize(image.width, image.height),
            IntOffset(x.roundToInt(), y.roundToInt()),
            IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
            paint,
        )
    }

    fun drawImageFull(canvas: Canvas, image: ImageBitmap, paint: Paint = smoothPaint()) {
        canvas.drawImage(image, Offset.Zero, paint)
    }

    fun smoothPaint() = Paint().apply {
        isAntiAlias = true
        filterQuality = FilterQuality.Low
    }

    /** Rasmni o'lchamini o'zgartirib nusxalaydi. */
    fun resized(image: ImageBitmap, width: Int, height: Int): ImageBitmap {
        val out = blank(width, height)
        drawScaled(Canvas(out), image, 0f, 0f, out.width.toFloat(), out.height.toFloat())
        return out
    }

    /** Tez taxminiy blur: kichraytirib, qayta kattalashtirish (ikki bosqich). */
    fun blur(image: ImageBitmap, radius: Float): ImageBitmap {
        if (radius < 0.5f) return image
        val factor = (radius / 2f).coerceIn(1f, 24f)
        val smallWidth = (image.width / factor).toInt().coerceAtLeast(1)
        val smallHeight = (image.height / factor).toInt().coerceAtLeast(1)
        val small = resized(image, smallWidth, smallHeight)
        val smaller = resized(small, (smallWidth / 2).coerceAtLeast(1), (smallHeight / 2).coerceAtLeast(1))
        val back = resized(smaller, smallWidth, smallHeight)
        return resized(back, image.width, image.height)
    }

    /** 4x5 rang matritsasini (ofset 0..255) qo'llaydi. */
    fun withColorMatrix(image: ImageBitmap, matrix: FloatArray): ImageBitmap {
        val out = blank(image.width, image.height)
        Canvas(out).drawImage(image, Offset.Zero, Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix(matrix)) })
        return out
    }

    fun colorMatrixPaint(matrix: FloatArray?): Paint = smoothPaint().apply {
        if (matrix != null) colorFilter = ColorFilter.colorMatrix(ColorMatrix(matrix))
    }

    fun copy(image: ImageBitmap): ImageBitmap {
        val out = blank(image.width, image.height)
        Canvas(out).drawImage(image, Offset.Zero, Paint())
        return out
    }

    /** [image] ni [dst] ustiga [mode] bilan to'liq o'lchamda chizadi. */
    fun composite(dst: ImageBitmap, image: ImageBitmap, mode: BlendMode, dx: Float = 0f, dy: Float = 0f, matrix: FloatArray? = null) {
        val paint = colorMatrixPaint(matrix).apply { blendMode = mode }
        Canvas(dst).drawImage(image, Offset(dx, dy), paint)
    }

    /**
     * Kadrni to'liq sig'dirib chizadi; bo'sh joy shu kadrning xiralashtirilgan,
     * kattalashtirilgan nusxasi bilan to'ldiriladi (Reels uslubi).
     */
    fun drawFitWithBlurredBackground(canvas: Canvas, frame: ImageBitmap, width: Float, height: Float, matrix: FloatArray?) {
        val fit = min(width / frame.width, height / frame.height)
        val fitWidth = frame.width * fit
        val fitHeight = frame.height * fit
        if (fitWidth < width - 1 || fitHeight < height - 1) {
            val fill = max(width / frame.width, height / frame.height)
            val background = blur(resized(frame, (frame.width / 4).coerceAtLeast(1), (frame.height / 4).coerceAtLeast(1)), 12f)
            val fillWidth = frame.width * fill
            val fillHeight = frame.height * fill
            drawScaled(canvas, background, (width - fillWidth) / 2, (height - fillHeight) / 2, fillWidth, fillHeight, colorMatrixPaint(matrix))
        }
        drawScaled(canvas, frame, (width - fitWidth) / 2, (height - fitHeight) / 2, fitWidth, fitHeight, colorMatrixPaint(matrix))
    }
}
