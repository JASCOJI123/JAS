package com.jas.montaj.render

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.jas.montaj.montaj.MontajState
import com.jas.montaj.motion.Easing
import com.jas.montaj.motion.LayerContent
import com.jas.montaj.motion.MotionLayer
import com.jas.montaj.motion.MotionProject
import com.jas.montaj.motion.valueAt
import com.jas.montaj.platform.FrameSource
import com.jas.montaj.platform.MediaBackend

/**
 * Motion kadrini chizadi: qatlamlar pastdan tepaga — maska, effekt, transform va blend mode bilan.
 * Ko'rish oynasi ham, eksport ham shu klassdan foydalanadi. Bir vaqtda bitta oqimdan chaqiriladi.
 */
class MotionRenderer(private val text: TextRenderer, private val media: MediaBackend) {
    private val imageCache = HashMap<String, ImageBitmap>()
    private val shapeCache = HashMap<LayerContent.Shape, ImageBitmap>()
    private val textCache = HashMap<LayerContent.Text, ImageBitmap>()
    private val layoutCache = HashMap<LayerContent.Text, TextRenderer.Layout>()

    /**
     * @param scale chiqish o'lchami / loyiha o'lchami (ko'rish oynasi uchun 0.5).
     * @param exactVideo eksportda aniq kadr, ijro paytida tezroq.
     */
    fun render(project: MotionProject, time: Double, scale: Float, frames: FrameSource, exactVideo: Boolean): ImageBitmap {
        val output = Graphics.blank((project.width * scale).toInt(), (project.height * scale).toInt())
        val canvas = Canvas(output)
        canvas.scale(scale, scale)
        canvas.drawRect(0f, 0f, project.width.toFloat(), project.height.toFloat(), Paint().apply { color = Color(project.background) })

        for (layer in project.layers) {
            if (!layer.isActive(time)) continue
            var image = content(layer, time - layer.inPoint, scale, frames, exactVideo) ?: continue
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
            val paint = Graphics.smoothPaint().apply {
                alpha = opacity.toFloat()
                blendMode = layer.blend.composeMode()
            }
            Graphics.drawScaled(canvas, image.image, -halfWidth, -halfHeight, image.logicalWidth, image.logicalHeight, paint)
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
        is LayerContent.Text -> layout(content).let { it.width.toFloat() to it.height.toFloat() }
    }

    private fun content(layer: MotionLayer, localTime: Double, scale: Float, frames: FrameSource, exactVideo: Boolean): LayerImage? =
        when (val content = layer.content) {
            is LayerContent.Video -> {
                val maxSide = (maxOf(content.width, content.height) * scale).toInt().coerceIn(64, 1920)
                frames.frame(content.path, localTime, maxSide, exactVideo)?.let { frame ->
                    LayerImage(frame, frame.width / content.width.toFloat().coerceAtLeast(1f))
                }
            }
            is LayerContent.Image -> (imageCache[content.path] ?: media.loadImage(content.path)?.also { imageCache[content.path] = it })
                ?.let { LayerImage(it, it.width / content.width.toFloat().coerceAtLeast(1f)) }
            is LayerContent.Shape -> LayerImage(shapeCache.getOrPut(content) { ShapeRenderer.render(content) })
            is LayerContent.Text -> {
                val layout = layout(content)
                if (text.isStatic(content, localTime)) {
                    LayerImage(textCache.getOrPut(content) { text.render(content, layout, Double.MAX_VALUE) })
                } else {
                    LayerImage(text.render(content, layout, localTime))
                }
            }
        }

    private fun layout(content: LayerContent.Text): TextRenderer.Layout = layoutCache.getOrPut(content) { text.layout(content) }

    /** Tahrirlash paytida eski variantlar to'planib qolmasligi uchun. */
    private fun trimCaches() {
        if (shapeCache.size > 40) shapeCache.clear()
        if (textCache.size > 40) textCache.clear()
        if (layoutCache.size > 40) layoutCache.clear()
        if (imageCache.size > 20) imageCache.clear()
    }
}

/**
 * Montaj kadri: joriy klip (to'liq sig'dirib, xira fon bilan), kliplar orasidagi o'tish effekti,
 * filtr va matnlar. Eksport ham, pauzadagi ko'rish oynasi ham shu klassdan foydalanadi.
 */
class MontajRenderer(private val text: TextRenderer) {
    private val width = MontajState.WIDTH.toFloat()
    private val height = MontajState.HEIGHT.toFloat()

    fun render(state: MontajState, time: Double, frames: FrameSource, maxSide: Int = 1920): ImageBitmap {
        val output = Graphics.blank(MontajState.WIDTH, MontajState.HEIGHT)
        val canvas = Canvas(output)
        canvas.drawRect(0f, 0f, width, height, Paint().apply { color = Color.Black })

        val transition = state.transitionAt(time)
        if (transition != null) {
            val (index, progress) = transition
            val from = clipFrame(state, index, time, frames, maxSide)
            val to = clipFrame(state, index + 1, time, frames, maxSide)
            Transitions.draw(canvas, state.clips[index].transition, from, to, Easing.EASE_IN_OUT.apply(progress).toFloat(), width, height)
        } else {
            val starts = state.clipStarts
            val index = starts.indexOfLast { it <= time }.coerceAtLeast(0)
            if (index < state.clips.size) Graphics.drawImageFull(canvas, clipFrame(state, index, time, frames, maxSide))
        }
        state.overlays.filter { time >= it.start && time < it.end }.forEach { text.drawOverlay(canvas, it, width, height) }
        return output
    }

    /**
     * Klipning [time] dagi kadri butun kadr o'lchamida (filtr bilan).
     * O'tish paytida klip chegarasidan tashqariga chiqsa, birinchi/oxirgi kadri ushlab turiladi.
     */
    private fun clipFrame(state: MontajState, index: Int, time: Double, frames: FrameSource, maxSide: Int): ImageBitmap {
        val layer = Graphics.blank(MontajState.WIDTH, MontajState.HEIGHT)
        val clip = state.clips[index]
        val start = state.clipStarts[index]
        val sourceTime = (clip.trimStart + (time - start) * clip.speed).coerceIn(clip.trimStart, maxOf(clip.trimStart, clip.trimEnd - 0.04))
        frames.frame(clip.path, sourceTime, maxSide, exact = true)?.let { frame ->
            val canvas = Canvas(layer)
            canvas.drawRect(0f, 0f, width, height, Paint().apply { color = Color.Black })
            Graphics.drawFitWithBlurredBackground(canvas, frame, width, height, state.filter.matrix())
        }
        return layer
    }
}
