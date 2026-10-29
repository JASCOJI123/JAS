package com.jas.montaj.montaj

import com.jas.montaj.core.Colors
import com.jas.montaj.core.newId
import kotlinx.serialization.Serializable

@Serializable
data class Clip(
    val id: String = newId(),
    val path: String,
    /** Manba videoning to'liq davomiyligi (soniya). */
    val durationSec: Double,
    val width: Int,
    val height: Int,
    val hasAudio: Boolean,
    val trimStart: Double = 0.0,
    val trimEnd: Double = durationSec,
    val speed: Double = 1.0,
) {
    val trimmedDuration: Double get() = (trimEnd - trimStart).coerceAtLeast(0.0)
    val outputDuration: Double get() = trimmedDuration / speed

    companion object {
        const val MIN_LENGTH = 0.3
        val SPEEDS = listOf(0.25, 0.5, 1.0, 1.5, 2.0, 3.0)
    }
}

@Serializable
enum class TextPosition(val title: String, val anchor: Float) {
    TOP("Tepa", 0.14f),
    CENTER("O'rta", 0.5f),
    BOTTOM("Past", 0.78f),
}

@Serializable
data class TextOverlayItem(
    val id: String = newId(),
    val text: String,
    /** Tayyor videodagi vaqt (soniya). */
    val start: Double,
    val end: Double,
    val position: TextPosition = TextPosition.BOTTOM,
    val fontSize: Float = 72f,
    val color: Int = Colors.WHITE,
    val hasBackground: Boolean = true,
)

@Serializable
data class MusicTrack(val path: String, val name: String, val durationSec: Double)

/**
 * Filtrlar 4x5 rang matritsasi sifatida (ofsetlar 0..255 shkalasida).
 * Ko'rish oynasi (ExoPlayer / AVPlayer) va eksport (umumiy renderer) bir xil matritsadan foydalanadi.
 */
@Serializable
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

    fun matrix(): FloatArray? = when (this) {
        NONE -> null
        VIVID -> ColorMatrices.concat(ColorMatrices.saturation(1.35f), ColorMatrices.contrast(1.08f))
        WARM -> ColorMatrices.scale(1.08f, 1f, 0.88f)
        COOL -> ColorMatrices.scale(0.9f, 1f, 1.1f)
        FADE -> ColorMatrices.concat(ColorMatrices.saturation(0.7f), ColorMatrices.contrast(0.75f), ColorMatrices.brightness(15f))
        RETRO -> ColorMatrices.concat(ColorMatrices.scale(1.06f, 1f, 0.82f), ColorMatrices.saturation(0.8f), ColorMatrices.contrast(0.9f))
        CHROME -> ColorMatrices.concat(ColorMatrices.saturation(1.2f), ColorMatrices.contrast(1.15f))
        MONO -> ColorMatrices.saturation(0f)
        NOIR -> ColorMatrices.concat(ColorMatrices.saturation(0f), ColorMatrices.contrast(1.35f))
    }
}

/** 4x5 rang matritsalari (qatorlar: R, G, B, A; oxirgi ustun — ofset 0..255). */
object ColorMatrices {
    fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

    fun scale(r: Float, g: Float, b: Float) = floatArrayOf(r, 0f, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

    fun saturation(s: Float): FloatArray {
        val lr = 0.213f * (1 - s)
        val lg = 0.715f * (1 - s)
        val lb = 0.072f * (1 - s)
        return floatArrayOf(lr + s, lg, lb, 0f, 0f, lr, lg + s, lb, 0f, 0f, lr, lg, lb + s, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    fun contrast(c: Float): FloatArray {
        val offset = (1 - c) * 127.5f
        return floatArrayOf(c, 0f, 0f, 0f, offset, 0f, c, 0f, 0f, offset, 0f, 0f, c, 0f, offset, 0f, 0f, 0f, 1f, 0f)
    }

    fun brightness(offset255: Float) =
        floatArrayOf(1f, 0f, 0f, 0f, offset255, 0f, 1f, 0f, 0f, offset255, 0f, 0f, 1f, 0f, offset255, 0f, 0f, 0f, 1f, 0f)

    /** Avval [matrices] ning birinchisi, keyin keyingilari qo'llanadi. */
    fun concat(vararg matrices: FloatArray): FloatArray = matrices.reduce { acc, next -> multiply(next, acc) }

    /** a × b (b avval qo'llanadi). */
    private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = if (col == 4) a[row * 5 + 4] else 0f
                for (k in 0 until 4) sum += a[row * 5 + k] * b[k * 5 + col]
                out[row * 5 + col] = sum
            }
        }
        return out
    }
}

/** Montaj loyihasining to'liq holati. */
@Serializable
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

    companion object {
        /** Vertikal 9:16 (Reels / TikTok / Shorts). */
        const val WIDTH = 1080
        const val HEIGHT = 1920
    }
}
