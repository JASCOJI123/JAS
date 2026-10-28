package com.jas.montaj.motion.model

import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow

data class Vec2(val x: Double, val y: Double) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
}

enum class Easing(val title: String) {
    LINEAR("Chiziqli"),
    EASE_IN("Ease In"),
    EASE_OUT("Ease Out"),
    EASE_IN_OUT("Easy Ease"),
    BACK("Overshoot"),
    HOLD("Hold");

    fun apply(t: Double): Double = when (this) {
        LINEAR -> t
        EASE_IN -> t * t * t
        EASE_OUT -> 1 - (1 - t).pow(3)
        EASE_IN_OUT -> if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
        BACK -> {
            val c1 = 1.70158
            val c3 = c1 + 1
            1 + c3 * (t - 1).pow(3) + c1 * (t - 1).pow(2)
        }
        HOLD -> 0.0
    }
}

data class Keyframe<T>(
    val id: String = UUID.randomUUID().toString(),
    /** Kompozitsiya vaqti (soniya). */
    val time: Double,
    val value: T,
    /** Shu keyframe'dan keyingisigacha bo'lgan harakat egri chizig'i. */
    val easing: Easing = Easing.EASE_IN_OUT,
)

/**
 * After Effects'dagi kabi animatsiya qilinadigan xususiyat:
 * keyframe bo'lmasa doimiy qiymat, bo'lsa vaqt bo'yicha hisoblanadi.
 */
data class Animatable<T>(
    val constant: T,
    val keyframes: List<Keyframe<T>> = emptyList(),
) {
    val isAnimated: Boolean get() = keyframes.isNotEmpty()
    val keyframeTimes: List<Double> get() = keyframes.map { it.time }

    fun keyframeIndex(time: Double): Int? =
        keyframes.indexOfFirst { abs(it.time - time) <= TOLERANCE }.takeIf { it >= 0 }

    fun valueAt(time: Double, lerp: (T, T, Double) -> T): T {
        val first = keyframes.firstOrNull() ?: return constant
        val last = keyframes.last()
        if (time <= first.time) return first.value
        if (time >= last.time) return last.value
        val index = keyframes.indexOfLast { it.time <= time }
        val from = keyframes[index]
        val to = keyframes.getOrNull(index + 1) ?: return last.value
        if (from.easing == Easing.HOLD) return from.value
        val span = to.time - from.time
        val progress = if (span > 0) (time - from.time) / span else 1.0
        return lerp(from.value, to.value, from.easing.apply(progress))
    }

    /** Animatsiya bo'lsa shu vaqtda keyframe yaratadi/yangilaydi, aks holda doimiy qiymatni o'zgartiradi. */
    fun set(value: T, time: Double): Animatable<T> {
        if (!isAnimated) return copy(constant = value)
        val index = keyframeIndex(time)
        return if (index != null) {
            copy(keyframes = keyframes.toMutableList().also { it[index] = it[index].copy(value = value) })
        } else {
            copy(keyframes = (keyframes + Keyframe(time = time, value = value)).sortedBy { it.time })
        }
    }

    /** ⏱ tugmasi: shu vaqtda keyframe bo'lsa o'chiradi, bo'lmasa joriy qiymat bilan qo'shadi. */
    fun toggleKeyframe(time: Double, lerp: (T, T, Double) -> T): Animatable<T> {
        val index = keyframeIndex(time)
        if (index != null) {
            val remaining = keyframes.filterIndexed { i, _ -> i != index }
            return copy(constant = if (remaining.isEmpty()) keyframes[index].value else constant, keyframes = remaining)
        }
        val keyframe = Keyframe(time = time, value = valueAt(time, lerp))
        return copy(keyframes = (keyframes + keyframe).sortedBy { it.time })
    }

    fun setEasing(easing: Easing, time: Double): Animatable<T> {
        val index = keyframeIndex(time) ?: return this
        return copy(keyframes = keyframes.toMutableList().also { it[index] = it[index].copy(easing = easing) })
    }

    companion object {
        /** Bir kadr (30 fps) ichidagi keyframe "shu vaqtdagi" hisoblanadi. */
        const val TOLERANCE = 1.0 / 60.0
    }
}

object Lerp {
    val double: (Double, Double, Double) -> Double = { from, to, p -> from + (to - from) * p }
    val vec2: (Vec2, Vec2, Double) -> Vec2 = { from, to, p -> Vec2(double(from.x, to.x, p), double(from.y, to.y, p)) }
}

@JvmName("valueAtDouble")
fun Animatable<Double>.valueAt(time: Double): Double = valueAt(time, Lerp.double)

@JvmName("valueAtVec2")
fun Animatable<Vec2>.valueAt(time: Double): Vec2 = valueAt(time, Lerp.vec2)

@JvmName("toggleDouble")
fun Animatable<Double>.toggleKeyframe(time: Double): Animatable<Double> = toggleKeyframe(time, Lerp.double)

@JvmName("toggleVec2")
fun Animatable<Vec2>.toggleKeyframe(time: Double): Animatable<Vec2> = toggleKeyframe(time, Lerp.vec2)
