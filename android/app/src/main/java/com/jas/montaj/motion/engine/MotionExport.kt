package com.jas.montaj.motion.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList
import com.jas.montaj.motion.model.MotionProject
import java.io.File

/**
 * Motion loyihasini Media3 Transformer orqali eksport qilish.
 * Qora rasm "tashuvchi" vaqt o'qini beradi, har bir kadrni esa [MotionRenderer] chizadi.
 */
object MotionExport {
    private const val FRAME_RATE = 30

    fun composition(context: Context, project: MotionProject, frames: VideoFrameSource): Composition {
        val carrier = carrierImage(context, project.width, project.height)
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(carrier))
            .setImageDurationMs((project.duration * 1000).toLong())
            .build()
        val item = EditedMediaItem.Builder(mediaItem)
            .setFrameRate(FRAME_RATE)
            .build()
        val overlay = MotionFrameOverlay(project, MotionRenderer(frames))
        return Composition.Builder(EditedMediaItemSequence(listOf(item)))
            .setEffects(Effects(emptyList(), listOf(OverlayEffect(ImmutableList.of(overlay)))))
            .build()
    }

    private fun carrierImage(context: Context, width: Int, height: Int): File {
        val file = File(context.cacheDir, "motion_carrier_${width}x$height.png")
        if (file.exists()) return file
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
}

/** Har bir eksport kadri uchun Motion loyihasini to'liq chizadi. */
private class MotionFrameOverlay(
    private val project: MotionProject,
    private val renderer: MotionRenderer,
) : BitmapOverlay() {
    override fun getBitmap(presentationTimeUs: Long): Bitmap =
        renderer.render(project, presentationTimeUs / 1_000_000.0, scale = 1f, exactVideo = true)
}
