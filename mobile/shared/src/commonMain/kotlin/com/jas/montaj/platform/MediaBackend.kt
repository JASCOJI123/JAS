package com.jas.montaj.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Platformaga xos yupqa media qatlami. Qolgan hamma narsa (UI, modellar, renderer)
 * umumiy kodda — iOS va Android'da bir xil.
 */
interface MediaBackend {
    fun videoInfo(path: String): VideoInfo
    fun audioDuration(path: String): Double
    fun loadImage(path: String): ImageBitmap?
    fun thumbnail(path: String, maxSide: Int = 320): ImageBitmap?

    /** Video kadrlarini olish manbai (renderer uchun). Ishlatib bo'lgach yopiladi. */
    fun openFrameSource(): FrameSource

    /** Montaj ko'rish oynasi uchun pleyer. */
    fun createPlayer(): PreviewPlayer

    /** Mikrofondan ovoz yozuvchi (voiceover). */
    fun createRecorder(): AudioRecorder

    /**
     * Kadrlarni [ExportJob.renderFrame] chizadi, platforma ularni videoga yozadi,
     * ovozlarni aralashtiradi va galereyaga saqlaydi.
     */
    suspend fun export(job: ExportJob, onProgress: (Float) -> Unit): ExportedVideo

    fun share(video: ExportedVideo)
}

data class VideoInfo(
    val durationSec: Double,
    /** Burilish hisobga olingan o'lcham. */
    val width: Int,
    val height: Int,
    val hasAudio: Boolean,
)

interface FrameSource {
    /**
     * @param timeSec manba videodagi vaqt.
     * @param exact eksportda aniq kadr; ijro paytida tezroq (yaqin kalit kadr) bo'lishi mumkin.
     */
    fun frame(path: String, timeSec: Double, maxSide: Int, exact: Boolean): ImageBitmap?
    fun close()
}

/** Ko'rish oynasi uchun klip ketma-ketligi. */
data class PreviewTimeline(
    val clips: List<PreviewClip>,
    /** 4x5 rang matritsasi (ofset 0..255) yoki null. */
    val colorMatrix: FloatArray?,
    val musicPath: String?,
    val musicDuration: Double,
    val originalVolume: Float,
    val musicVolume: Float,
    val voiceovers: List<PreviewVoice> = emptyList(),
)

/** Tayyor videoning [startAt] soniyasidan ijro etiladigan ovoz yozuvi. */
data class PreviewVoice(val path: String, val startAt: Double, val duration: Double, val volume: Float)

data class PreviewClip(val path: String, val start: Double, val end: Double, val speed: Double)

interface PreviewPlayer {
    val isPlaying: Boolean
    /** Tayyor videodagi joriy vaqt (soniya). */
    fun currentTime(): Double
    fun setTimeline(timeline: PreviewTimeline, resumeAt: Double)
    fun play()
    fun pause()
    fun seek(timeSec: Double)
    fun release()
}

interface AudioRecorder {
    /** Yozishni boshlaydi; muvaffaqiyatsiz bo'lsa false. */
    fun start(): Boolean
    /** Yozishni to'xtatadi va fayl yo'lini qaytaradi. */
    fun stop(): String?
}

/** Bitta ovoz bo'lagi: manbaning [sourceStart]..[sourceEnd] qismi, tayyor videoning [startAt] soniyasidan. */
data class AudioSegment(
    val path: String,
    val sourceStart: Double,
    val sourceEnd: Double,
    val startAt: Double,
    val speed: Double = 1.0,
    val volume: Float = 1f,
    /** Musiqa: video oxirigacha takrorlanadi. */
    val loop: Boolean = false,
)

class ExportJob(
    val width: Int,
    val height: Int,
    val fps: Int,
    val durationSec: Double,
    val audio: List<AudioSegment>,
    /** Bitta oqimdan, vaqt o'sib boradigan tartibda chaqiriladi. */
    val renderFrame: (timeSec: Double, frames: FrameSource) -> ImageBitmap,
)

/** Galereyaga saqlangan video (ulashish uchun platforma manzili). */
class ExportedVideo(val handle: String)

expect fun mediaBackend(): MediaBackend

enum class PickKind { VIDEO, IMAGE, AUDIO }

/**
 * Galereya / fayllardan tanlash. Tanlanganlar ilovaning doimiy papkasiga nusxalanadi
 * va ularning yo'llari qaytariladi.
 * @return tanlash oynasini ochuvchi funksiya.
 */
@Composable
expect fun rememberMediaPicker(kind: PickKind, multiple: Boolean, onPicked: (List<String>) -> Unit): () -> Unit

/**
 * Mikrofon ruxsatini so'raydi.
 * @return so'rovni boshlovchi funksiya; natija [onResult] ga keladi.
 */
@Composable
expect fun rememberMicrophonePermission(onResult: (Boolean) -> Unit): () -> Unit

/** Montaj pleyeri ko'rsatiladigan platforma view'i. */
@Composable
expect fun PlayerSurface(player: PreviewPlayer, modifier: Modifier)
