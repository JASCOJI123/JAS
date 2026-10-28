package com.jas.montaj.motion.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import com.jas.montaj.motion.model.LayerContent
import com.jas.montaj.motion.model.MotionLayer
import com.jas.montaj.motion.model.MotionProject
import com.jas.montaj.motion.model.valueAt
import java.io.File
import kotlin.math.abs

/**
 * Bitta kadrni chizadi: qatlamlarni pastdan tepaga — maska, effekt, transform va blend mode bilan.
 * Ko'rish oynasi ham, eksport ham shu klassdan foydalanadi.
 * Bir vaqtda faqat bitta oqimdan chaqiriladi.
 */
class MotionRenderer(private val frames: VideoFrameSource) {
    private val imageCache = HashMap<File, Bitmap>()
    private val shapeCache = HashMap<LayerContent.Shape, Bitmap>()
    private val textCache = HashMap<LayerContent.Text, Bitmap>()
    private val layoutCache = HashMap<LayerContent.Text, TextLayoutData>()

    /**
     * @param scale chiqish o'lchami / loyiha o'lchami (ko'rish oynasi uchun 0.5).
     * @param exactVideo eksportda aniq kadr, ijro paytida esa tezroq (yaqin kalit kadr).
     */
    fun render(project: MotionProject, time: Double, scale: Float, exactVideo: Boolean): Bitmap {
        val width = (project.width * scale).toInt().coerceAtLeast(1)
        val height = (project.height * scale).toInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.scale(scale, scale)
        canvas.drawColor(project.background)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        for (layer in project.layers) {
            if (!layer.isActive(time)) continue
            var image = content(layer, time - layer.inPoint, scale, exactVideo) ?: continue
            layer.mask?.let { image = MaskRenderer.apply(it, image) }
            for (effect in layer.effects) {
                if (effect.enabled) image = EffectRenderer.apply(effect, image, time)
            }
            val opacity = layer.opacity.valueAt(time).coerceIn(0.0, 1.0)
            if (opacity <= 0.001) continue

            val position = layer.position.valueAt(time) + EffectRenderer.shakeOffset(layer.effects, time)
            val layerScale = layer.scale.valueAt(time).toFloat()
            val halfWidth = image.logicalWidth / 2
            val halfHeight = image.logicalHeight / 2

            canvas.save()
            canvas.translate(position.x.toFloat(), position.y.toFloat())
            canvas.rotate(layer.rotation.valueAt(time).toFloat())
            canvas.scale(layerScale, layerScale)
            paint.alpha = (opacity * 255).toInt()
            paint.blendMode = layer.blend.mode
            canvas.drawBitmap(image.bitmap, null, RectF(-halfWidth, -halfHeight, halfWidth, halfHeight), paint)
            canvas.restore()
        }
        trimCaches()
        return output
    }

    /** Ko'rish oynasidagi tanlov ramkasi uchun qatlamning mantiqiy o'lchami. */
    fun contentSize(layer: MotionLayer): Pair<Float, Float> = when (val content = layer.content) {
        is LayerContent.Video -> content.width.toFloat() to content.height.toFloat()
        is LayerContent.Image -> content.width.toFloat() to content.height.toFloat()
        is LayerContent.Shape -> content.width to content.height
        is LayerContent.Text -> textLayout(content).let { it.width.toFloat() to it.height.toFloat() }
    }

    private fun content(layer: MotionLayer, localTime: Double, scale: Float, exactVideo: Boolean): LayerImage? =
        when (val content = layer.content) {
            is LayerContent.Video -> {
                val maxSide = (maxOf(content.width, content.height) * scale).toInt().coerceIn(64, 1920)
                frames.frame(content.file, (localTime * 1_000_000).toLong(), maxSide, exactVideo)?.let { frame ->
                    LayerImage(frame, frame.width / content.width.toFloat().coerceAtLeast(1f))
                }
            }
            is LayerContent.Image -> imageCache.getOrPut(content.file) {
                BitmapFactory.decodeFile(content.file.absolutePath) ?: return null
            }.let { LayerImage(it) }
            is LayerContent.Shape -> LayerImage(shapeCache.getOrPut(content) { ShapeRenderer.render(content) })
            is LayerContent.Text -> {
                val layout = textLayout(content)
                if (TextRenderer.isStatic(content, localTime)) {
                    LayerImage(textCache.getOrPut(content) { TextRenderer.render(content, layout, Double.MAX_VALUE) })
                } else {
                    LayerImage(TextRenderer.render(content, layout, localTime))
                }
            }
        }

    @Synchronized
    private fun textLayout(content: LayerContent.Text): TextLayoutData = layoutCache.getOrPut(content) { TextRenderer.layout(content) }

    /** Tahrirlash paytida eski variantlar to'planib qolmasligi uchun. */
    private fun trimCaches() {
        if (shapeCache.size > 40) shapeCache.clear()
        if (textCache.size > 40) textCache.clear()
        if (layoutCache.size > 40) synchronized(this) { layoutCache.clear() }
        if (imageCache.size > 20) imageCache.clear()
    }
}

/** Video qatlamlar uchun kadr olish (MediaMetadataRetriever). */
class VideoFrameSource {
    private val retrievers = HashMap<File, MediaMetadataRetriever>()
    private val lastFrames = HashMap<File, CachedFrame>()

    private class CachedFrame(val timeUs: Long, val maxSide: Int, val bitmap: Bitmap)

    @Synchronized
    fun frame(file: File, timeUs: Long, maxSide: Int, exact: Boolean): Bitmap? {
        lastFrames[file]?.let { cached ->
            if (cached.maxSide == maxSide && abs(cached.timeUs - timeUs) < 15_000) return cached.bitmap
        }
        val retriever = retrievers.getOrPut(file) {
            MediaMetadataRetriever().apply { setDataSource(file.absolutePath) }
        }
        val option = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        val bitmap = try {
            retriever.getScaledFrameAtTime(timeUs, option, maxSide, maxSide)
        } catch (error: RuntimeException) {
            null
        } ?: return lastFrames[file]?.bitmap
        lastFrames[file] = CachedFrame(timeUs, maxSide, bitmap)
        return bitmap
    }

    @Synchronized
    fun release() {
        retrievers.values.forEach { it.release() }
        retrievers.clear()
        lastFrames.clear()
    }
}
