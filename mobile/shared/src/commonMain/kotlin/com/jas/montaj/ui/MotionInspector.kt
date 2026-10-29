package com.jas.montaj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jas.montaj.core.Format
import com.jas.montaj.motion.MotionEditor
import com.jas.montaj.motion.Animatable
import com.jas.montaj.motion.AnimationPreset
import com.jas.montaj.motion.Bezier
import com.jas.montaj.motion.Keyframe
import com.jas.montaj.motion.Wiggle
import com.jas.montaj.motion.isDescendant
import androidx.compose.material3.HorizontalDivider
import com.jas.montaj.motion.EffectKind
import com.jas.montaj.motion.Easing
import com.jas.montaj.motion.LayerBlend
import com.jas.montaj.motion.LayerContent
import com.jas.montaj.motion.LayerEffect
import com.jas.montaj.motion.LayerMask
import com.jas.montaj.motion.MaskShape
import com.jas.montaj.motion.MotionLayer
import com.jas.montaj.motion.ShapeKind
import com.jas.montaj.motion.TextAnimation
import com.jas.montaj.motion.Vec2
import com.jas.montaj.motion.toggleKeyframe
import com.jas.montaj.motion.valueAt

// region Transform

@Composable
internal fun TransformTab(
    layer: MotionLayer,
    time: Double,
    width: Double,
    height: Double,
    update: ((MotionLayer) -> MotionLayer) -> Unit,
    onPreset: (AnimationPreset) -> Unit,
) {
    PresetMenu(onPreset)
    val position = layer.position.valueAt(time)
    val positionIndex = layer.position.keyframeIndex(time)
    PropertyHeader(
        title = "Joyi",
        valueText = "${position.x.toInt()}, ${position.y.toInt()}",
        isAnimated = layer.position.isAnimated,
        keyframe = positionIndex?.let { layer.position.keyframes[it] },
        onToggle = { update { it.copy(position = it.position.toggleKeyframe(time)) } },
        onEasing = { easing -> update { it.copy(position = it.position.setEasing(easing, time)) } },
        onBezier = { bezier -> update { it.copy(position = it.position.setBezier(bezier, time)) } },
    )
    LabeledSlider("X", position.x.toFloat(), (-width * 0.5).toFloat()..(width * 1.5).toFloat(), "", { x ->
        update { it.copy(position = it.position.set(Vec2(x.toDouble(), it.position.valueAt(time).y), time)) }
    })
    LabeledSlider("Y", position.y.toFloat(), (-height * 0.5).toFloat()..(height * 1.5).toFloat(), "", { y ->
        update { it.copy(position = it.position.set(Vec2(it.position.valueAt(time).x, y.toDouble()), time)) }
    })
    AnimatableRow("O'lcham", layer.scale, time, 0.02..4.0, Format::percent) { value -> update { it.copy(scale = value) } }
    AnimatableRow("Burilish", layer.rotation, time, -360.0..360.0, Format::degrees) { value -> update { it.copy(rotation = value) } }
    AnimatableRow("Shaffoflik", layer.opacity, time, 0.0..1.0, Format::percent) { value -> update { it.copy(opacity = value) } }
    HintText("Maslahat: ko'rish oynasida qatlamni barmoq bilan suring, ikki barmoq bilan kattalashtiring yoki buring.")
}

/** Animatsiya qilinadigan son xususiyati: slider + keyframe + easing. */
@Composable
fun AnimatableRow(
    title: String,
    property: Animatable<Double>,
    time: Double,
    range: ClosedFloatingPointRange<Double>,
    format: (Double) -> String,
    onChange: (Animatable<Double>) -> Unit,
) {
    val value = property.valueAt(time)
    val index = property.keyframeIndex(time)
    PropertyHeader(
        title = title,
        valueText = format(value),
        isAnimated = property.isAnimated,
        keyframe = index?.let { property.keyframes[it] },
        onToggle = { onChange(property.toggleKeyframe(time)) },
        onEasing = { onChange(property.setEasing(it, time)) },
        onBezier = { onChange(property.setBezier(it, time)) },
    )
    androidx.compose.material3.Slider(
        value = value.coerceIn(range.start, range.endInclusive).toFloat(),
        onValueChange = { onChange(property.set(it.toDouble(), time)) },
        valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
    )
}

@Composable
private fun PropertyHeader(
    title: String,
    valueText: String,
    isAnimated: Boolean,
    keyframe: Keyframe<*>?,
    onToggle: () -> Unit,
    onEasing: (Easing) -> Unit,
    onBezier: (Bezier?) -> Unit,
) {
    var showGraph by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
            val icon = when {
                keyframe != null -> Icons.Filled.Diamond
                isAnimated -> Icons.Outlined.Diamond
                else -> Icons.Filled.Timer
            }
            Icon(icon, contentDescription = "Keyframe", tint = if (isAnimated) Color(0xFFFFD91A) else Color.Gray)
        }
        Text(title, style = MaterialTheme.typography.bodyMedium)
        if (keyframe != null) {
            Spacer(Modifier.size(6.dp))
            EasingMenu(if (keyframe.bezier != null) "Grafik" else keyframe.easing.title, onEasing) { showGraph = true }
        }
        Spacer(Modifier.weight(1f))
        Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showGraph && keyframe != null) {
        GraphEditorDialog(
            initial = keyframe.bezier ?: Bezier.presets.first { it.first == "Easy Ease" }.second,
            onApply = onBezier,
            onDismiss = { showGraph = false },
        )
    }
}

@Composable
private fun EasingMenu(current: String, onSelect: (Easing) -> Unit, onGraph: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(current, style = MaterialTheme.typography.labelSmall) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Easing.entries.forEach { easing ->
                DropdownMenuItem(text = { Text(easing.title) }, onClick = { open = false; onSelect(easing) })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Graph editor…") }, onClick = { open = false; onGraph() })
        }
    }
}

@Composable
private fun PresetMenu(onPreset: (AnimationPreset) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("✨ Animatsiya preseti") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AnimationPreset.entries.forEach { preset ->
                DropdownMenuItem(text = { Text(preset.title) }, onClick = { open = false; onPreset(preset) })
            }
        }
    }
    HintText("Preset joriy vaqtdan boshlab keyframe'lar qo'yadi.")
}

// endregion

// region Kontent

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContentTab(layer: MotionLayer, update: ((MotionLayer) -> MotionLayer) -> Unit) {
    when (val content = layer.content) {
        is LayerContent.Text -> {
            val set: (LayerContent.Text) -> Unit = { newContent -> update { it.copy(content = newContent) } }
            OutlinedTextField(
                value = content.text,
                onValueChange = { set(content.copy(text = it)) },
                label = { Text("Matn") },
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledSlider("Shrift o'lchami", content.fontSize, 30f..300f, "${content.fontSize.toInt()} px", { set(content.copy(fontSize = it)) })
            SwitchRow("Qalin", content.bold) { set(content.copy(bold = it)) }
            SwitchRow("Soya", content.shadow) { set(content.copy(shadow = it)) }
            ColorPaletteRow("Rang", content.color) { set(content.copy(color = it)) }
            SectionTitle("Matn animatsiyasi")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextAnimation.entries.forEach { animation ->
                    FilterChip(
                        selected = content.animation == animation,
                        onClick = { set(content.copy(animation = animation)) },
                        label = { Text(animation.title) },
                    )
                }
            }
            if (content.animation != TextAnimation.NONE && content.animation != TextAnimation.WAVE) {
                LabeledSlider(
                    "Davomiyligi",
                    content.animationDuration.toFloat(),
                    0.2f..5f,
                    Format.seconds(content.animationDuration),
                    { set(content.copy(animationDuration = it.toDouble())) },
                )
            }
            HintText("Animatsiya qatlam boshlanganda (In) ishga tushadi.")
        }
        is LayerContent.Shape -> {
            val set: (LayerContent.Shape) -> Unit = { newContent -> update { it.copy(content = newContent) } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShapeKind.entries.forEach { kind ->
                    FilterChip(selected = content.kind == kind, onClick = { set(content.copy(kind = kind)) }, label = { Text(kind.title) })
                }
            }
            LabeledSlider("Eni", content.width, 10f..2160f, "${content.width.toInt()} px", { set(content.copy(width = it)) })
            LabeledSlider("Bo'yi", content.height, 10f..3840f, "${content.height.toInt()} px", { set(content.copy(height = it)) })
            ColorPaletteRow("To'ldirish rangi", content.fill) { set(content.copy(fill = it)) }
            LabeledSlider("Chegara qalinligi", content.strokeWidth, 0f..80f, "${content.strokeWidth.toInt()} px", { set(content.copy(strokeWidth = it)) })
            if (content.strokeWidth > 0) ColorPaletteRow("Chegara rangi", content.strokeColor) { set(content.copy(strokeColor = it)) }
        }
        is LayerContent.Video -> {
            HintText("Video: ${content.width}×${content.height}, ${Format.seconds(content.durationSec)}")
            LabeledSlider("Ovoz balandligi", content.volume, 0f..1f, Format.percent(content.volume.toDouble()), { volume ->
                update { it.copy(content = content.copy(volume = volume)) }
            })
        }
        is LayerContent.Image -> HintText("Rasm: ${content.width}×${content.height} px")
    }
}

// endregion

// region Effektlar

@Composable
internal fun EffectsTab(layer: MotionLayer, time: Double, update: ((MotionLayer) -> MotionLayer) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { open = true }) { Text("Effekt qo'shish") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EffectKind.entries.forEach { kind ->
                DropdownMenuItem(text = { Text(kind.title) }, onClick = {
                    open = false
                    update { it.copy(effects = it.effects + LayerEffect(kind = kind)) }
                })
            }
        }
    }
    if (layer.effects.isEmpty()) {
        HintText("Effektlar tartib bilan qo'llanadi. Miqdorini ham keyframe bilan animatsiya qilish mumkin.")
    }
    layer.effects.forEach { effect ->
        val setEffect: (LayerEffect) -> Unit = { changed ->
            update { current -> current.copy(effects = current.effects.map { if (it.id == effect.id) changed else it }) }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.Gray.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(effect.kind.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = effect.enabled, onCheckedChange = { setEffect(effect.copy(enabled = it)) })
                IconButton(onClick = { update { current -> current.copy(effects = current.effects.filterNot { it.id == effect.id }) } }) {
                    Icon(Icons.Filled.Delete, contentDescription = "O'chirish", tint = MaterialTheme.colorScheme.error)
                }
            }
            AnimatableRow("Miqdor", effect.amount, time, effect.kind.min..effect.kind.max, Format::twoDecimals) { amount ->
                setEffect(effect.copy(amount = amount))
            }
        }
    }
}

// endregion

// region Maska

@Composable
internal fun MaskTab(layer: MotionLayer, update: ((MotionLayer) -> MotionLayer) -> Unit) {
    val mask = layer.mask
    if (mask == null) {
        Button(onClick = { update { it.copy(mask = LayerMask()) } }) { Text("Maska qo'shish") }
        HintText("Maska qatlamning faqat bir qismini ko'rsatadi. Teskari qilsangiz, aksincha o'sha qismni yashiradi.")
        return
    }
    val set: (LayerMask) -> Unit = { changed -> update { it.copy(mask = changed) } }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MaskShape.entries.forEach { shape ->
            FilterChip(selected = mask.shape == shape, onClick = { set(mask.copy(shape = shape)) }, label = { Text(shape.title) })
        }
    }
    LabeledSlider("Chapdan (X)", mask.x.toFloat(), -0.5f..1f, Format.percent(mask.x), { set(mask.copy(x = it.toDouble())) })
    LabeledSlider("Tepadan (Y)", mask.y.toFloat(), -0.5f..1f, Format.percent(mask.y), { set(mask.copy(y = it.toDouble())) })
    LabeledSlider("Eni", mask.width.toFloat(), 0.02f..1.5f, Format.percent(mask.width), { set(mask.copy(width = it.toDouble())) })
    LabeledSlider("Bo'yi", mask.height.toFloat(), 0.02f..1.5f, Format.percent(mask.height), { set(mask.copy(height = it.toDouble())) })
    LabeledSlider("Feather (yumshoqlik)", mask.feather.toFloat(), 0f..200f, Format.pixels(mask.feather), { set(mask.copy(feather = it.toDouble())) })
    SwitchRow("Teskari (Inverted)", mask.inverted) { set(mask.copy(inverted = it)) }
    TextButton(onClick = { update { it.copy(mask = null) } }) {
        Text("Maskani olib tashlash", color = MaterialTheme.colorScheme.error)
    }
}

// endregion

// region Bog'lash (parenting, wiggle, loop)

@Composable
internal fun LinkTab(editor: MotionEditor, layer: MotionLayer, update: ((MotionLayer) -> MotionLayer) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val project = editor.project
    val parent = project.layers.firstOrNull { it.id == layer.parentId }
    SectionTitle("Parent (ota-qatlam)")
    Box {
        OutlinedButton(onClick = { open = true }) { Text(parent?.name ?: "Yo'q") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Yo'q") }, onClick = { open = false; editor.setParent(layer.id, null) })
            project.layers.filter { it.id != layer.id && !project.isDescendant(it.id, of = layer.id) }.forEach { candidate ->
                DropdownMenuItem(text = { Text(candidate.name) }, onClick = { open = false; editor.setParent(layer.id, candidate.id) })
            }
        }
    }
    HintText("Bog'langan qatlam ota-qatlam bilan birga suriladi, buriladi va kattalashadi (null object kabi).")

    SectionTitle("Wiggle (tebranish)")
    val wiggle = layer.wiggle
    val setWiggle: (Wiggle) -> Unit = { changed -> update { it.copy(wiggle = changed) } }
    LabeledSlider("Chastota", wiggle.frequency.toFloat(), 0.2f..12f, "${Format.decimal(wiggle.frequency, 1)} /s", { setWiggle(wiggle.copy(frequency = it.toDouble())) })
    LabeledSlider("Joy", wiggle.position.toFloat(), 0f..200f, Format.pixels(wiggle.position), { setWiggle(wiggle.copy(position = it.toDouble())) })
    LabeledSlider("Burilish", wiggle.rotation.toFloat(), 0f..45f, Format.degrees(wiggle.rotation), { setWiggle(wiggle.copy(rotation = it.toDouble())) })
    LabeledSlider("O'lcham", wiggle.scale.toFloat(), 0f..0.5f, Format.percent(wiggle.scale), { setWiggle(wiggle.copy(scale = it.toDouble())) })
    HintText("AE'dagi wiggle(chastota, miqdor) ekspressiyasi: silliq tasodifiy harakat.")

    SectionTitle("Loop")
    SwitchRow("Animatsiyani takrorlash (loopOut)", layer.loopKeyframes) { on -> update { it.copy(loopKeyframes = on) } }
    HintText("Oxirgi keyframe'dan keyin animatsiya birinchisidan qayta boshlanadi.")
}

// endregion

// region Qatlam

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LayerTab(editor: MotionEditor, layer: MotionLayer, update: ((MotionLayer) -> MotionLayer) -> Unit) {
    val duration = editor.project.duration
    OutlinedTextField(
        value = layer.name,
        onValueChange = { name -> update { it.copy(name = name) } },
        label = { Text("Nomi") },
        modifier = Modifier.fillMaxWidth(),
    )
    SectionTitle("Blend mode")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LayerBlend.entries.forEach { blend ->
            FilterChip(selected = layer.blend == blend, onClick = { update { it.copy(blend = blend) } }, label = { Text(blend.title) })
        }
    }
    LabeledSlider("Boshlanishi (In)", layer.inPoint.toFloat(), 0f..duration.toFloat(), Format.seconds(layer.inPoint), { value ->
        update { it.copy(inPoint = minOf(value.toDouble(), it.outPoint - 0.1)) }
    })
    LabeledSlider("Tugashi (Out)", minOf(layer.outPoint, duration).toFloat(), 0f..duration.toFloat(), Format.seconds(layer.outPoint), { value ->
        update { it.copy(outPoint = maxOf(value.toDouble(), it.inPoint + 0.1)) }
    })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { editor.moveLayer(layer.id, 1) }) { Text("Oldinga") }
        OutlinedButton(onClick = { editor.moveLayer(layer.id, -1) }) { Text("Orqaga") }
        OutlinedButton(onClick = { editor.duplicateLayer(layer.id) }) { Text("Nusxa") }
    }
    TextButton(onClick = { editor.deleteLayer(layer.id) }) {
        Text("Qatlamni o'chirish", color = MaterialTheme.colorScheme.error)
    }
}

// endregion
