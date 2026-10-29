package com.jas.montaj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jas.montaj.core.Format
import kotlin.math.ceil

/** CapCut uslubidagi ranglar. */
object CapCut {
    val Background = Color(0xFF000000)
    val Panel = Color(0xFF141414)
    val Surface = Color(0xFF242426)
    val Track = Color(0xFF1C1C1E)
    val Accent = Color(0xFF20D5EC)
    val Export = Color(0xFF3B6CFF)
    val TextTrack = Color(0xFF8B5CF6)
    val AudioTrack = Color(0xFF1DB37A)
    val VoiceTrack = Color(0xFFFF9F0A)
    val Muted = Color(0xFF8E8E93)
}

/** Tepa panel: ✕, loyiha nomi, sifat belgisi va Eksport tugmasi. */
@Composable
fun EditorTopBar(
    title: String,
    onClose: () -> Unit,
    onExport: () -> Unit,
    exportEnabled: Boolean,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Yopish") }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
        Box(
            Modifier.background(CapCut.Surface, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        ) { Text("1080P", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onExport,
            enabled = exportEnabled,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CapCut.Export, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        ) { Text("Eksport", fontWeight = FontWeight.Bold) }
    }
}

/** Ko'rish oynasi ostidagi qator: vaqt, ▶ va o'ng tomonda (masalan ↩ ↪). */
@Composable
fun TransportBar(current: Double, total: Double, playing: Boolean, onToggle: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp)) {
        Text(
            "${Format.preciseClock(current)} / ${Format.preciseClock(total)}",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        IconButton(onClick = onToggle, modifier = Modifier.align(Alignment.Center)) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Ijro", modifier = Modifier.size(30.dp))
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** Pastki asboblar qatori (belgi + yozuv). [onBack] bo'lsa chapda "‹" — tanlovni bekor qilish. */
@Composable
fun BottomToolbar(onBack: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CapCut.Panel).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Orqaga", modifier = Modifier.size(30.dp)) }
            Box(Modifier.width(1.dp).height(36.dp).background(CapCut.Surface))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), content = content)
    }
}

@Composable
fun ToolButton(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier
            .width(68.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val color = if (enabled) tint else CapCut.Muted.copy(alpha = 0.5f)
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Asbob paneli: sarlavha va ✓ tugmasi bilan (CapCut'dagi pastdan chiqadigan panel). */
@Composable
fun ToolPanel(title: String, onDone: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(CapCut.Panel)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onDone) { Icon(Icons.Filled.Check, contentDescription = "Tayyor", tint = CapCut.Accent) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** Timeline elementlarini joylash: vaqt → markazdagi kursorga nisbatan x. */
class TimelineScope(private val centerX: Dp, private val currentTime: Double, val pixelsPerSecond: Dp) {
    fun x(time: Double): Dp = centerX + pixelsPerSecond * (time - currentTime).toFloat()
    fun width(seconds: Double): Dp = pixelsPerSecond * seconds.toFloat()
}

/**
 * CapCut timeline'i: o'rtada qo'zg'almas oq kursor, kontent esa vaqt bilan suriladi.
 * Bir barmoq — vaqt bo'ylab surish, ikki barmoq — zoom.
 */
@Composable
fun TimelineViewport(
    currentTime: Double,
    duration: Double,
    pixelsPerSecond: Dp,
    onSeek: (Double) -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(TimelineScope) -> Unit,
) {
    val time by rememberUpdatedState(currentTime)
    val scale by rememberUpdatedState(pixelsPerSecond)
    val seek by rememberUpdatedState(onSeek)
    val zoom by rememberUpdatedState(onZoom)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(CapCut.Background)
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    if (gestureZoom != 1f) zoom(gestureZoom)
                    val perSecond = scale.toPx().coerceAtLeast(1f)
                    seek((time - pan.x / perSecond).coerceIn(0.0, duration.coerceAtLeast(0.0)))
                }
            },
    ) {
        val scope = TimelineScope(maxWidth / 2, currentTime, pixelsPerSecond)
        Column(Modifier.fillMaxWidth()) {
            Ruler(scope, duration)
            content(scope)
        }
        Box(Modifier.align(Alignment.TopCenter).width(2.dp).fillMaxHeight().background(Color.White))
        Box(Modifier.align(Alignment.TopCenter).padding(top = 18.dp).size(10.dp).background(Color.White, CircleShape))
    }
}

/** Soniya belgilari: zoom'ga qarab 1, 2 yoki 5 soniyada bir. */
@Composable
private fun Ruler(scope: TimelineScope, duration: Double) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = CapCut.Muted, fontSize = 10.sp)
    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
        val perSecond = scope.pixelsPerSecond.toPx()
        val step = when {
            perSecond >= 50f -> 1.0
            perSecond >= 22f -> 2.0
            else -> 5.0
        }
        val first = 0.0
        val last = ceil(duration / step) * step
        var tick = first
        while (tick <= last + 0.001) {
            val x = scope.x(tick).toPx()
            if (x in -40f..size.width + 40f) {
                drawCircle(CapCut.Muted, radius = 1.5f, center = Offset(x, size.height - 4f))
                val label = measurer.measure(Format.clock(tick), labelStyle)
                drawText(label, topLeft = Offset(x - label.size.width / 2f, 0f))
            }
            tick += step
        }
        // Oraliq nuqtalar.
        var half = first + step / 2
        while (half <= last) {
            val x = scope.x(half).toPx()
            if (x in 0f..size.width) drawCircle(CapCut.Surface, radius = 1.5f, center = Offset(x, size.height - 4f))
            half += step
        }
    }
}

/** Trek ustidagi kichik "+ ..." tugmasi (bo'sh trek uchun). */
@Composable
fun TrackHint(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(26.dp)
            .background(CapCut.Surface, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelSmall, color = CapCut.Muted) }
}
