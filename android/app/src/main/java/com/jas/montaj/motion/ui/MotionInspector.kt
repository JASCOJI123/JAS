package com.jas.montaj.motion.ui

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
import com.jas.montaj.common.ColorPaletteRow
import com.jas.montaj.common.Formats
import com.jas.montaj.common.HintText
import com.jas.montaj.common.LabeledSlider
import com.jas.montaj.common.SectionTitle
import com.jas.montaj.motion.MotionViewModel
import com.jas.montaj.motion.model.Animatable
import com.jas.montaj.motion.model.EffectKind
import com.jas.montaj.motion.model.Easing
import com.jas.montaj.motion.model.LayerBlend
import com.jas.montaj.motion.model.LayerContent
import com.jas.montaj.motion.model.LayerEffect
import com.jas.montaj.motion.model.LayerMask
import com.jas.montaj.motion.model.MaskShape
import com.jas.montaj.motion.model.MotionLayer
import com.jas.montaj.motion.model.ShapeKind
import com.jas.montaj.motion.model.TextAnimation
import com.jas.montaj.motion.model.Vec2
import com.jas.montaj.motion.model.toggleKeyframe
import com.jas.montaj.motion.model.valueAt

private val tabs = listOf("Transform", "Kontent", "Effekt", "Mask", "Qatlam")

@Composable
fun LayerInspector(viewModel: MotionViewModel, layer: MotionLayer) {
    var tab by remember { mutableIntStateOf(0) }
    val update: ((MotionLayer) -> MotionLayer) -> Unit = { change -> viewModel.updateLayer(layer.id, change) }
    val time = viewModel.currentTime

    ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
        tabs.forEachIndexed { index, title ->
            Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
        }
    }
    when (tab) {
        0 -> TransformTab(layer, time, viewModel.project.width.toDouble(), viewModel.project.height.toDouble(), update)
        1 -> ContentTab(layer, update)
        2 -> EffectsTab(layer, time, update)
        3 -> MaskTab(layer, update)
        else -> LayerTab(viewModel, layer, update)
    }
}

// region Transform

@Composable
private fun TransformTab(layer: MotionLayer, time: Double, width: Double, height: Double, update: ((MotionLayer) -> MotionLayer) -> Unit) {
    val position = layer.position.valueAt(time)
    PropertyHeader(
        title = "Joyi",
        valueText = "${position.x.toInt()}, ${position.y.toInt()}",
        isAnimated = layer.position.isAnimated,
        keyframeIndex = layer.position.keyframeIndex(time),
        easing = layer.position.keyframeIndex(time)?.let { layer.position.keyframes[it].easing },
        onToggle = { update { it.copy(position = it.position.toggleKeyframe(time)) } },
        onEasing = { easing -> update { it.copy(position = it.position.setEasing(easing, time)) } },
    )
    LabeledSlider("X", position.x.toFloat(), (-width * 0.5).toFloat()..(width * 1.5).toFloat(), "", { x ->
        update { it.copy(position = it.position.set(Vec2(x.toDouble(), it.position.valueAt(time).y), time)) }
    })
    LabeledSlider("Y", position.y.toFloat(), (-height * 0.5).toFloat()..(height * 1.5).toFloat(), "", { y ->
        update { it.copy(position = it.position.set(Vec2(it.position.valueAt(time).x, y.toDouble()), time)) }
    })
    AnimatableRow("O'lcham", layer.scale, time, 0.02..4.0, Formats::percent) { value -> update { it.copy(scale = value) } }
    AnimatableRow("Burilish", layer.rotation, time, -360.0..360.0, Formats::degrees) { value -> update { it.copy(rotation = value) } }
    AnimatableRow("Shaffoflik", layer.opacity, time, 0.0..1.0, Formats::percent) { value -> update { it.copy(opacity = value) } }
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
        keyframeIndex = index,
        easing = index?.let { property.keyframes[it].easing },
        onToggle = { onChange(property.toggleKeyframe(time)) },
        onEasing = { onChange(property.setEasing(it, time)) },
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
    keyframeIndex: Int?,
    easing: Easing?,
    onToggle: () -> Unit,
    onEasing: (Easing) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
            val icon = when {
                keyframeIndex != null -> Icons.Filled.Diamond
                isAnimated -> Icons.Outlined.Diamond
                else -> Icons.Filled.Timer
            }
            Icon(icon, contentDescription = "Keyframe", tint = if (isAnimated) Color(0xFFFFD91A) else Color.Gray)
        }
        Text(title, style = MaterialTheme.typography.bodyMedium)
        if (easing != null) {
            Spacer(Modifier.size(6.dp))
            EasingMenu(easing, onEasing)
        }
        Spacer(Modifier.weight(1f))
        Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EasingMenu(current: Easing, onSelect: (Easing) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(current.title, style = MaterialTheme.typography.labelSmall) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Easing.entries.forEach { easing ->
                DropdownMenuItem(text = { Text(easing.title) }, onClick = { open = false; onSelect(easing) })
            }
        }
    }
}

// endregion

// region Kontent

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentTab(layer: MotionLayer, update: ((MotionLayer) -> MotionLayer) -> Unit) {
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
                    Formats.seconds(content.animationDuration),
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
        is LayerContent.Video -> HintText(
            "Video: ${content.width}×${content.height}, ${Formats.seconds(content.durationSec)}. " +
                "Motion rejimida video qatlamlar ovozsiz chiqadi.",
        )
        is LayerContent.Image -> HintText("Rasm: ${content.width}×${content.height} px")
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// endregion

// region Effektlar

@Composable
private fun EffectsTab(layer: MotionLayer, time: Double, update: ((MotionLayer) -> MotionLayer) -> Unit) {
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
            AnimatableRow("Miqdor", effect.amount, time, effect.kind.min..effect.kind.max, Formats::decimal) { amount ->
                setEffect(effect.copy(amount = amount))
            }
        }
    }
}

// endregion

// region Maska

@Composable
private fun MaskTab(layer: MotionLayer, update: ((MotionLayer) -> MotionLayer) -> Unit) {
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
    LabeledSlider("Chapdan (X)", mask.x.toFloat(), -0.5f..1f, Formats.percent(mask.x), { set(mask.copy(x = it.toDouble())) })
    LabeledSlider("Tepadan (Y)", mask.y.toFloat(), -0.5f..1f, Formats.percent(mask.y), { set(mask.copy(y = it.toDouble())) })
    LabeledSlider("Eni", mask.width.toFloat(), 0.02f..1.5f, Formats.percent(mask.width), { set(mask.copy(width = it.toDouble())) })
    LabeledSlider("Bo'yi", mask.height.toFloat(), 0.02f..1.5f, Formats.percent(mask.height), { set(mask.copy(height = it.toDouble())) })
    LabeledSlider("Feather (yumshoqlik)", mask.feather.toFloat(), 0f..200f, Formats.pixels(mask.feather), { set(mask.copy(feather = it.toDouble())) })
    SwitchRow("Teskari (Inverted)", mask.inverted) { set(mask.copy(inverted = it)) }
    TextButton(onClick = { update { it.copy(mask = null) } }) {
        Text("Maskani olib tashlash", color = MaterialTheme.colorScheme.error)
    }
}

// endregion

// region Qatlam

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerTab(viewModel: MotionViewModel, layer: MotionLayer, update: ((MotionLayer) -> MotionLayer) -> Unit) {
    val duration = viewModel.project.duration
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
    LabeledSlider("Boshlanishi (In)", layer.inPoint.toFloat(), 0f..duration.toFloat(), Formats.seconds(layer.inPoint), { value ->
        update { it.copy(inPoint = minOf(value.toDouble(), it.outPoint - 0.1)) }
    })
    LabeledSlider("Tugashi (Out)", minOf(layer.outPoint, duration).toFloat(), 0f..duration.toFloat(), Formats.seconds(layer.outPoint), { value ->
        update { it.copy(outPoint = maxOf(value.toDouble(), it.inPoint + 0.1)) }
    })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { viewModel.moveLayer(layer.id, 1) }) { Text("Oldinga") }
        OutlinedButton(onClick = { viewModel.moveLayer(layer.id, -1) }) { Text("Orqaga") }
        OutlinedButton(onClick = { viewModel.duplicateLayer(layer.id) }) { Text("Nusxa") }
    }
    TextButton(onClick = { viewModel.deleteLayer(layer.id) }) {
        Text("Qatlamni o'chirish", color = MaterialTheme.colorScheme.error)
    }
}

// endregion
