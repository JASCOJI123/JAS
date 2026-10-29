package com.jas.montaj.motion

import com.jas.montaj.core.Colors

/** Bir bosishda keyframe'lar qo'yadigan tayyor animatsiyalar (joriy vaqtdan boshlab). */
enum class AnimationPreset(val title: String) {
    FADE_IN("Paydo bo'lish"),
    FADE_OUT("G'oyib bo'lish"),
    POP_IN("Sakrab chiqish"),
    ZOOM_IN("Zoom bilan kirish"),
    SLIDE_LEFT("O'ngdan kirish"),
    SLIDE_RIGHT("Chapdan kirish"),
    SLIDE_UP("Pastdan kirish"),
    DROP_IN("Yuqoridan tushish"),
    SPIN_IN("Aylanib kirish"),
    BOUNCE("Sakrash"),
    PULSE("Puls (yurak urishi)");

    fun apply(layer: MotionLayer, time: Double, project: MotionProject): MotionLayer {
        val d = 0.6
        val position = layer.position.valueAt(time)
        val scale = layer.scale.valueAt(time)
        val rotation = layer.rotation.valueAt(time)
        val opacity = layer.opacity.valueAt(time).takeIf { it > 0.01 } ?: 1.0
        fun Animatable<Double>.key(at: Double, value: Double, easing: Easing = Easing.EASE_OUT) = withKeyframe(at, value, easing)
        fun Animatable<Vec2>.key(at: Double, value: Vec2, easing: Easing = Easing.EASE_OUT) = withKeyframe(at, value, easing)

        return when (this) {
            FADE_IN -> layer.copy(opacity = layer.opacity.key(time, 0.0).key(time + d, opacity))
            FADE_OUT -> layer.copy(opacity = layer.opacity.key(time, opacity).key(time + d, 0.0))
            POP_IN -> layer.copy(
                scale = layer.scale.key(time, 0.0, Easing.BACK).key(time + d, scale),
                opacity = layer.opacity.key(time, 0.0).key(time + 0.15, opacity),
            )
            ZOOM_IN -> layer.copy(
                scale = layer.scale.key(time, scale * 2.5).key(time + d, scale),
                opacity = layer.opacity.key(time, 0.0).key(time + d, opacity),
            )
            SLIDE_LEFT -> layer.copy(position = layer.position.key(time, Vec2(position.x + project.width, position.y)).key(time + d, position))
            SLIDE_RIGHT -> layer.copy(position = layer.position.key(time, Vec2(position.x - project.width, position.y)).key(time + d, position))
            SLIDE_UP -> layer.copy(
                position = layer.position.key(time, Vec2(position.x, position.y + project.height * 0.4)).key(time + d, position),
                opacity = layer.opacity.key(time, 0.0).key(time + d, opacity),
            )
            DROP_IN -> layer.copy(position = layer.position.key(time, Vec2(position.x, position.y - project.height * 0.6), Easing.BACK).key(time + d, position))
            SPIN_IN -> layer.copy(
                rotation = layer.rotation.key(time, rotation - 360).key(time + d, rotation),
                scale = layer.scale.key(time, 0.0).key(time + d, scale),
            )
            BOUNCE -> layer.copy(
                position = layer.position
                    .key(time, Vec2(position.x, position.y - 300), Easing.EASE_IN)
                    .key(time + 0.35, position, Easing.EASE_OUT)
                    .key(time + 0.5, Vec2(position.x, position.y - 80), Easing.EASE_IN)
                    .key(time + 0.65, position),
            )
            PULSE -> layer.copy(
                scale = layer.scale
                    .key(time, scale, Easing.EASE_IN_OUT)
                    .key(time + 0.25, scale * 1.15, Easing.EASE_IN_OUT)
                    .key(time + 0.5, scale, Easing.EASE_IN_OUT),
                loopKeyframes = true,
            )
        }
    }
}

/** Tayyor shablonlar: bir nechta qatlam va animatsiyadan iborat. */
enum class MotionTemplate(val title: String) {
    INTRO("Intro sarlavha"),
    LOWER_THIRD("Lower third (ism, kasb)"),
    SUBSCRIBE("Obuna bo'ling tugmasi"),
    COUNTDOWN("3-2-1 sanoq");

    fun create(project: MotionProject, start: Double): List<MotionLayer> {
        val center = project.center
        val end = project.duration
        fun text(name: String, value: String, size: Float, at: Vec2, animation: TextAnimation = TextAnimation.POP, color: Int = Colors.WHITE, from: Double = start, to: Double = end) =
            MotionLayer(
                name = name,
                content = LayerContent.Text(text = value, fontSize = size, color = color, animation = animation),
                inPoint = from,
                outPoint = to,
                position = Animatable(at),
                scale = Animatable(1.0),
            )
        fun shape(name: String, kind: ShapeKind, width: Float, height: Float, fill: Int, at: Vec2, from: Double = start, to: Double = end) =
            MotionLayer(
                name = name,
                content = LayerContent.Shape(kind, width, height, fill),
                inPoint = from,
                outPoint = to,
                position = Animatable(at),
                scale = Animatable(1.0),
            )

        return when (this) {
            INTRO -> listOf(
                shape("Fon", ShapeKind.RECTANGLE, project.width.toFloat(), project.height.toFloat(), 0xFF1E1E2E.toInt(), center),
                AnimationPreset.SLIDE_RIGHT.apply(
                    shape("Chiziq", ShapeKind.ROUNDED, 520f, 18f, Colors.ORANGE, Vec2(center.x, center.y + 110)), start + 0.3, project,
                ),
                text("Sarlavha", "SARLAVHA", 150f, Vec2(center.x, center.y - 40)),
                text("Izoh", "Bu yerga izoh yozing", 60f, Vec2(center.x, center.y + 200), TextAnimation.FADE, from = start + 0.6),
            )
            LOWER_THIRD -> {
                val y = project.height * 0.78
                listOf(
                    AnimationPreset.SLIDE_RIGHT.apply(
                        shape("Panel", ShapeKind.ROUNDED, 760f, 210f, 0xE6000000.toInt(), Vec2(center.x - 60, y)), start, project,
                    ),
                    AnimationPreset.SLIDE_RIGHT.apply(
                        shape("Urg'u", ShapeKind.RECTANGLE, 14f, 170f, Colors.ORANGE, Vec2(center.x - 420, y)), start + 0.1, project,
                    ),
                    text("Ism", "Ism Familiya", 72f, Vec2(center.x - 60, y - 35), TextAnimation.SLIDE_UP, from = start + 0.3),
                    text("Kasb", "Kasbi / lavozimi", 46f, Vec2(center.x - 60, y + 45), TextAnimation.FADE, color = 0xFFBBBBBB.toInt(), from = start + 0.5),
                )
            }
            SUBSCRIBE -> {
                val at = Vec2(center.x, project.height * 0.8)
                listOf(
                    AnimationPreset.POP_IN.apply(shape("Tugma", ShapeKind.ROUNDED, 640f, 170f, 0xFFFF3B30.toInt(), at), start, project),
                    text("Yozuv", "OBUNA BO'LING", 78f, at, TextAnimation.POP, from = start + 0.2),
                )
            }
            COUNTDOWN -> (0 until 3).map { index ->
                val from = start + index
                AnimationPreset.POP_IN.apply(
                    text("Son ${3 - index}", "${3 - index}", 420f, center, TextAnimation.NONE, color = Colors.YELLOW, from = from, to = from + 1),
                    from,
                    project,
                )
            }
        }
    }
}
