package com.jas.montaj.montaj

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList

/** Montaj holatidan Media3 Transformer kompozitsiyasini quradi (eksport uchun). */
object MontajComposer {
    /** Vertikal 9:16 (Reels / TikTok / Shorts). */
    const val WIDTH = 1080
    const val HEIGHT = 1920

    fun build(state: MontajState): Composition {
        val clipItems = state.clips.map { clip ->
            val audio = mutableListOf<AudioProcessor>()
            val video = mutableListOf<Effect>()
            if (clip.speed != 1f) {
                audio += SonicAudioProcessor().apply {
                    setSpeed(clip.speed)
                    setPitch(1f)
                }
                video += SpeedChangeEffect(clip.speed)
            }
            audio += volumeProcessor(state.originalVolume)
            EditedMediaItem.Builder(clipMediaItem(clip))
                .setEffects(Effects(audio, video))
                .build()
        }

        val sequences = mutableListOf(EditedMediaItemSequence(clipItems))
        state.music?.let { music ->
            val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(music.file)))
                .setRemoveVideo(true)
                .setEffects(Effects(listOf(volumeProcessor(state.musicVolume)), emptyList()))
                .build()
            // Musiqa video uzunligigacha takrorlanadi.
            sequences += EditedMediaItemSequence(listOf(musicItem), true)
        }

        val videoEffects = mutableListOf<Effect>(
            Presentation.createForWidthAndHeight(WIDTH, HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT),
        )
        videoEffects += state.filter.effects()
        if (state.overlays.isNotEmpty()) {
            videoEffects += OverlayEffect(ImmutableList.of(TimedTextOverlay(state.overlays, WIDTH, HEIGHT)))
        }

        return Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), videoEffects))
            // Ovozsiz kliplar bo'lsa ham audio trek bir xil bo'lishi uchun.
            .experimentalSetForceAudioTrack(true)
            .build()
    }

    fun clipMediaItem(clip: Clip): MediaItem = MediaItem.Builder()
        .setUri(Uri.fromFile(clip.file))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs((clip.trimStart * 1000).toLong())
                .setEndPositionMs((clip.trimEnd * 1000).toLong())
                .build(),
        )
        .build()

    private fun volumeProcessor(volume: Float): ChannelMixingAudioProcessor = ChannelMixingAudioProcessor().apply {
        putChannelMixingMatrix(ChannelMixingMatrix.create(1, 2).scaleBy(volume))
        putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(volume))
    }
}

/** Vaqtga bog'langan matnlar: har kadrda faqat shu paytda ko'rinadiganlari chiziladi. */
class TimedTextOverlay(
    private val overlays: List<TextOverlayItem>,
    private val width: Int,
    private val height: Int,
) : BitmapOverlay() {
    private var lastKey: List<String>? = null
    private var lastBitmap: Bitmap? = null

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val time = presentationTimeUs / 1_000_000.0
        val active = overlays.filter { time >= it.start && time < it.end }
        val key = active.map { it.id }
        val cached = lastBitmap
        if (cached != null && key == lastKey) return cached

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        active.forEach { TextOverlayPainter.draw(canvas, it, width.toFloat(), height.toFloat()) }
        lastKey = key
        lastBitmap = bitmap
        return bitmap
    }
}

/** Matnni chizish: eksport va ko'rish oynasi bir xil funksiyadan foydalanadi. */
object TextOverlayPainter {
    fun draw(canvas: Canvas, overlay: TextOverlayItem, width: Float, height: Float) {
        val text = overlay.text.trim()
        if (text.isEmpty()) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = overlay.color
            textSize = overlay.fontSize
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 900, false)
            if (!overlay.hasBackground) setShadowLayer(10f, 0f, 3f, 0xB3000000.toInt())
        }
        val maxWidth = (width * 0.86f).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        var textWidth = 0f
        for (line in 0 until layout.lineCount) textWidth = maxOf(textWidth, layout.getLineWidth(line))

        val left = (width - maxWidth) / 2f
        val top = (height * overlay.position.anchor - layout.height / 2f).coerceIn(0f, height - layout.height)

        if (overlay.hasBackground) {
            val padding = overlay.fontSize * 0.35f
            val boxLeft = (width - textWidth) / 2f - padding
            val box = RectF(boxLeft, top - padding * 0.6f, boxLeft + textWidth + padding * 2, top + layout.height + padding * 0.6f)
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8C000000.toInt() }
            canvas.drawRoundRect(box, padding, padding, boxPaint)
        }
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }
}
