package com.jas.montaj.motion

import com.jas.montaj.core.Colors
import com.jas.montaj.core.newId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TextAnimation(val title: String) {
    NONE("Yo'q"),
    TYPEWRITER("Yozuv mashinkasi"),
    FADE("Harfma-harf paydo bo'lish"),
    SLIDE_UP("Pastdan chiqish"),
    POP("Sakrab chiqish"),
    WAVE("To'lqin"),
}

@Serializable
enum class ShapeKind(val title: String) {
    RECTANGLE("To'rtburchak"),
    ROUNDED("Yumaloq burchak"),
    ELLIPSE("Ellips"),
    TRIANGLE("Uchburchak"),
    STAR("Yulduz"),
}

@Serializable
sealed interface LayerContent {
    @Serializable
    @SerialName("video")
    data class Video(
        val path: String,
        val durationSec: Double,
        /** Burilish hisobga olingan o'lcham. */
        val width: Int,
        val height: Int,
        val volume: Float = 1f,
    ) : LayerContent

    @Serializable
    @SerialName("image")
    data class Image(val path: String, val width: Int, val height: Int) : LayerContent

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String = "Matn",
        val fontSize: Float = 110f,
        val bold: Boolean = true,
        val color: Int = Colors.WHITE,
        val shadow: Boolean = true,
        val animation: TextAnimation = TextAnimation.POP,
        /** Barcha harflar chiqib bo'lishi uchun ketadigan vaqt (soniya). */
        val animationDuration: Double = 1.2,
    ) : LayerContent

    @Serializable
    @SerialName("shape")
    data class Shape(
        val kind: ShapeKind,
        val width: Float = 500f,
        val height: Float = 500f,
        val fill: Int = Colors.ORANGE,
        val strokeColor: Int = Colors.WHITE,
        val strokeWidth: Float = 0f,
    ) : LayerContent
}

@Serializable
enum class LayerBlend(val title: String) {
    NORMAL("Normal"),
    ADD("Add"),
    SCREEN("Screen"),
    MULTIPLY("Multiply"),
    OVERLAY("Overlay"),
    SOFT_LIGHT("Soft Light"),
    LIGHTEN("Lighten"),
    DARKEN("Darken"),
    DIFFERENCE("Difference"),
}

@Serializable
enum class EffectKind(val title: String, val min: Double, val max: Double, val default: Double) {
    BLUR("Blur", 0.0, 60.0, 12.0),
    GLOW("Glow", 0.0, 60.0, 20.0),
    CHROMATIC("Chromatic aberration", 0.0, 40.0, 10.0),
    SHAKE("Shake", 0.0, 80.0, 20.0),
    BRIGHTNESS("Yorqinlik", -0.5, 0.5, 0.15),
    SATURATION("To'yinganlik", 0.0, 2.0, 1.5),
    HUE("Rang tusi (Hue)", -180.0, 180.0, 60.0),
    VIGNETTE("Vignette", 0.0, 3.0, 1.2),
}

@Serializable
data class LayerEffect(
    val id: String = newId(),
    val kind: EffectKind,
    val amount: Animatable<Double> = Animatable(kind.default),
    val enabled: Boolean = true,
)

@Serializable
enum class MaskShape(val title: String) { RECTANGLE("To'rtburchak"), ELLIPSE("Ellips") }

/** Qatlam ichidagi maska. Koordinatalar 0..1 (qatlam o'lchamiga nisbatan, tepa-chapdan). */
@Serializable
data class LayerMask(
    val shape: MaskShape = MaskShape.ELLIPSE,
    val x: Double = 0.1,
    val y: Double = 0.1,
    val width: Double = 0.8,
    val height: Double = 0.8,
    val feather: Double = 20.0,
    val inverted: Boolean = false,
)

/** AE'dagi wiggle() ekspressiyasi: silliq tasodifiy tebranish. */
@Serializable
data class Wiggle(
    /** Soniyasiga necha marta. */
    val frequency: Double = 2.0,
    /** Piksel. */
    val position: Double = 0.0,
    /** Gradus. */
    val rotation: Double = 0.0,
    /** Ulush (0.1 = ±10%). */
    val scale: Double = 0.0,
) {
    val isActive: Boolean get() = position > 0 || rotation > 0 || scale > 0
}

@Serializable
data class MotionLayer(
    val id: String = newId(),
    val name: String,
    val content: LayerContent,
    val inPoint: Double,
    val outPoint: Double,
    val visible: Boolean = true,
    /** Kadrdagi markaz nuqtasi (piksel, tepa-chapdan). */
    val position: Animatable<Vec2>,
    /** 1.0 = 100%. */
    val scale: Animatable<Double>,
    /** Gradus, soat yo'nalishida. */
    val rotation: Animatable<Double> = Animatable(0.0),
    /** 0..1. */
    val opacity: Animatable<Double> = Animatable(1.0),
    val blend: LayerBlend = LayerBlend.NORMAL,
    val effects: List<LayerEffect> = emptyList(),
    val mask: LayerMask? = null,
    /** Parenting: shu qatlam ota-qatlam bilan birga harakatlanadi (pozitsiya ota koordinatalarida). */
    val parentId: String? = null,
    val wiggle: Wiggle = Wiggle(),
    /** loopOut(): oxirgi keyframe'dan keyin animatsiya qaytadan boshlanadi. */
    val loopKeyframes: Boolean = false,
) {
    fun isActive(time: Double): Boolean = visible && time >= inPoint && time < outPoint

    /** Timeline'da ko'rsatish uchun barcha keyframe vaqtlari. */
    val allKeyframeTimes: List<Double>
        get() = position.keyframeTimes + scale.keyframeTimes + rotation.keyframeTimes + opacity.keyframeTimes +
            effects.flatMap { it.amount.keyframeTimes }
}

@Serializable
data class MotionProject(
    val width: Int = 1080,
    val height: Int = 1920,
    val duration: Double = 10.0,
    val background: Int = Colors.BLACK,
    /** 0-indeks — eng pastki qatlam. */
    val layers: List<MotionLayer> = emptyList(),
) {
    val center: Vec2 get() = Vec2(width / 2.0, height / 2.0)
}
