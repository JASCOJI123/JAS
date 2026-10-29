package com.jas.montaj.motion

import com.jas.montaj.render.EffectRenderer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Qatlamning kadrdagi holati: markaz, burilish (gradus), o'lcham. */
data class LayerTransform(val position: Vec2, val rotation: Double, val scale: Double)

/** Qatlamning o'z transformi: keyframe'lar, loop, wiggle va shake bilan. */
fun MotionLayer.localTransform(time: Double): LayerTransform {
    val t = if (loopKeyframes) loopedTime(time) else time
    var position = position.valueAt(t) + EffectRenderer.shakeOffset(effects, time)
    var rotation = rotation.valueAt(t)
    var scale = scale.valueAt(t)
    if (wiggle.isActive) {
        val seed = id.hashCode()
        val phase = time * wiggle.frequency
        position += Vec2(Noise.smooth(phase, seed) * wiggle.position, Noise.smooth(phase, seed + 1) * wiggle.position)
        rotation += Noise.smooth(phase, seed + 2) * wiggle.rotation
        scale *= 1 + Noise.smooth(phase, seed + 3) * wiggle.scale
    }
    return LayerTransform(position, rotation, scale)
}

/** loopOut("cycle"): birinchi va oxirgi keyframe orasini takrorlaydi. */
private fun MotionLayer.loopedTime(time: Double): Double {
    val times = allKeyframeTimes
    val first = times.minOrNull() ?: return time
    val last = times.maxOrNull() ?: return time
    val span = last - first
    if (span <= 0.01 || time <= last) return time
    return first + (time - first) % span
}

/** Parenting bilan dunyo transformi: ota-qatlamlar zanjiri bo'ylab yig'iladi. */
fun MotionProject.worldTransform(layer: MotionLayer, time: Double, depth: Int = 0): LayerTransform {
    val local = layer.localTransform(time)
    val parent = layer.parentId?.let { id -> layers.firstOrNull { it.id == id } }
    if (parent == null || depth > 16) return local
    val world = worldTransform(parent, time, depth + 1)
    val rotated = rotate(local.position, world.rotation)
    return LayerTransform(
        position = Vec2(world.position.x + rotated.x * world.scale, world.position.y + rotated.y * world.scale),
        rotation = world.rotation + local.rotation,
        scale = world.scale * local.scale,
    )
}

/** Kadrdagi siljishni ota-qatlam koordinatalariga aylantiradi (barmoq bilan surishda). */
fun MotionProject.toParentSpace(layer: MotionLayer, delta: Vec2, time: Double): Vec2 {
    val parent = layer.parentId?.let { id -> layers.firstOrNull { it.id == id } } ?: return delta
    val world = worldTransform(parent, time)
    val rotated = rotate(delta, -world.rotation)
    val scale = world.scale.takeIf { kotlin.math.abs(it) > 1e-4 } ?: 1.0
    return Vec2(rotated.x / scale, rotated.y / scale)
}

/** Qatlamni ota-qatlamga bog'laydi: ekranda joyi o'zgarmasligi uchun pozitsiya qayta hisoblanadi. */
fun MotionProject.setParent(layerId: String, parentId: String?, time: Double): MotionProject {
    val layer = layers.firstOrNull { it.id == layerId } ?: return this
    if (parentId != null && isDescendant(parentId, of = layerId)) return this
    val worldNow = worldTransform(layer, time)
    val detached = layer.copy(parentId = null)
    val target = parentId?.let { id -> layers.firstOrNull { it.id == id } }
    val newLocal = if (target == null) {
        worldNow.position
    } else {
        val parentWorld = worldTransform(target, time)
        val delta = Vec2(worldNow.position.x - parentWorld.position.x, worldNow.position.y - parentWorld.position.y)
        val rotated = rotate(delta, -parentWorld.rotation)
        Vec2(rotated.x / parentWorld.scale, rotated.y / parentWorld.scale)
    }
    val current = detached.position.valueAt(time)
    val shift = Vec2(newLocal.x - current.x, newLocal.y - current.y)
    // Barcha keyframe'larni bir xil siljitamiz — harakat shakli saqlanadi.
    val position = Animatable(
        constant = detached.position.constant + shift,
        keyframes = detached.position.keyframes.map { it.copy(value = it.value + shift) },
    )
    val updated = detached.copy(parentId = parentId, position = position)
    return copy(layers = layers.map { if (it.id == layerId) updated else it })
}

/** [candidate] qatlam [of] ning avlodimi (yoki o'zimi) — aylanma bog'lanishni oldini olish uchun. */
fun MotionProject.isDescendant(candidate: String, of: String): Boolean {
    var current: String? = candidate
    var guard = 0
    while (current != null && guard++ < 32) {
        if (current == of) return true
        current = layers.firstOrNull { it.id == current }?.parentId
    }
    return false
}

private fun rotate(point: Vec2, degrees: Double): Vec2 {
    if (degrees == 0.0) return point
    val radians = degrees * PI / 180
    val c = cos(radians)
    val s = sin(radians)
    // Ekran koordinatalari (Y pastga): musbat burchak — soat yo'nalishida.
    return Vec2(point.x * c - point.y * s, point.x * s + point.y * c)
}

/** Silliq 1D shovqin (−1..1), har bir urug' uchun takrorlanuvchan. */
object Noise {
    fun smooth(t: Double, seed: Int): Double {
        val i = floor(t).toInt()
        val f = t - i
        val u = f * f * (3 - 2 * f)
        return hash(i, seed) * (1 - u) + hash(i + 1, seed) * u
    }

    private fun hash(i: Int, seed: Int): Double {
        var x = i * 374761393 + seed * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0x7fffffff) / 2147483647.0 * 2 - 1
    }
}

/** Qatlamni [delta] soniyaga suradi: In/Out va barcha keyframe'lar birga. */
fun MotionLayer.shifted(delta: Double): MotionLayer {
    fun <T> Animatable<T>.moved() = copy(keyframes = keyframes.map { it.copy(time = it.time + delta) })
    return copy(
        inPoint = inPoint + delta,
        outPoint = outPoint + delta,
        position = position.moved(),
        scale = scale.moved(),
        rotation = rotation.moved(),
        opacity = opacity.moved(),
        effects = effects.map { it.copy(amount = it.amount.moved()) },
    )
}
