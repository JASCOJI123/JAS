package com.jas.montaj.montaj

import android.graphics.Color
import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import java.io.File
import java.util.UUID

data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val file: File,
    /** Manba videoning to'liq davomiyligi (soniya). */
    val durationSec: Double,
    val hasAudio: Boolean,
    val trimStart: Double = 0.0,
    val trimEnd: Double = durationSec,
    val speed: Float = 1f,
) {
    val trimmedDuration: Double get() = (trimEnd - trimStart).coerceAtLeast(0.0)
    val outputDuration: Double get() = trimmedDuration / speed

    companion object {
        const val MIN_LENGTH = 0.3
        val SPEEDS = listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 3f)
    }
}

enum class TextPosition(val title: String, val anchor: Float) {
    TOP("Tepa", 0.14f),
    CENTER("O'rta", 0.5f),
    BOTTOM("Past", 0.78f),
}

data class TextOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    /** Tayyor videodagi vaqt (soniya). */
    val start: Double,
    val end: Double,
    val position: TextPosition = TextPosition.BOTTOM,
    val fontSize: Float = 72f,
    val color: Int = Color.WHITE,
    val hasBackground: Boolean = true,
)

data class MusicTrack(val file: File, val name: String, val durationSec: Double)

enum class VideoFilter(val title: String) {
    NONE("Asl"),
    VIVID("Yorqin"),
    WARM("Iliq"),
    COOL("Sovuq"),
    FADE("Xira"),
    RETRO("Retro"),
    CHROME("Xrom"),
    MONO("Oq-qora"),
    NOIR("Nuar");

    /** Media3 effektlari: ko'rish (ExoPlayer) va eksport (Transformer) uchun bir xil. */
    fun effects(): List<Effect> = when (this) {
        NONE -> emptyList()
        VIVID -> listOf(HslAdjustment.Builder().adjustSaturation(35f).build(), Contrast(0.08f))
        WARM -> listOf(RgbAdjustment.Builder().setRedScale(1.08f).setBlueScale(0.88f).build())
        COOL -> listOf(RgbAdjustment.Builder().setRedScale(0.9f).setBlueScale(1.1f).build())
        FADE -> listOf(Contrast(-0.25f), Brightness(0.06f), HslAdjustment.Builder().adjustSaturation(-30f).build())
        RETRO -> listOf(
            RgbAdjustment.Builder().setRedScale(1.06f).setBlueScale(0.82f).build(),
            Contrast(-0.1f),
            HslAdjustment.Builder().adjustSaturation(-20f).build(),
        )
        CHROME -> listOf(HslAdjustment.Builder().adjustSaturation(20f).build(), Contrast(0.15f))
        MONO -> listOf(RgbFilter.createGrayscaleFilter())
        NOIR -> listOf(RgbFilter.createGrayscaleFilter(), Contrast(0.35f))
    }

    /** Filtr tanlash kartochkasidagi taxminiy ko'rinish (android.graphics.ColorMatrix). */
    fun previewMatrix(): android.graphics.ColorMatrix = android.graphics.ColorMatrix().apply {
        when (this@VideoFilter) {
            NONE -> Unit
            VIVID -> setSaturation(1.35f)
            WARM -> setScale(1.08f, 1f, 0.88f, 1f)
            COOL -> setScale(0.9f, 1f, 1.1f, 1f)
            FADE -> setSaturation(0.7f)
            RETRO -> setScale(1.06f, 1f, 0.82f, 1f)
            CHROME -> setSaturation(1.2f)
            MONO, NOIR -> setSaturation(0f)
        }
    }
}

/** Loyihaning to'liq holati: kompozitsiya shundan quriladi. */
data class MontajState(
    val clips: List<Clip> = emptyList(),
    val overlays: List<TextOverlayItem> = emptyList(),
    val filter: VideoFilter = VideoFilter.NONE,
    val music: MusicTrack? = null,
    val originalVolume: Float = 1f,
    val musicVolume: Float = 0.6f,
) {
    val totalDuration: Double get() = clips.sumOf { it.outputDuration }

    /** Har bir klip tayyor videoda nechanchi soniyadan boshlanadi. */
    val clipStarts: List<Double>
        get() {
            var cursor = 0.0
            return clips.map { clip -> cursor.also { cursor += clip.outputDuration } }
        }
}
