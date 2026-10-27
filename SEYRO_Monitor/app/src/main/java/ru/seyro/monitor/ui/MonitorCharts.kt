package ru.seyro.monitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.seyro.monitor.data.MetricSample
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.abs

internal val CpuSeriesColor = Color(0xFF1767F5)
internal val RamSeriesColor = Color(0xFF9B6CF6)
internal val DiskSeriesColor = Color(0xFF39A979)
internal val NetworkInColor = Color(0xFF16A6A1)
internal val NetworkOutColor = Color(0xFFF08A24)
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun MetricChart(
    title: String,
    unit: String,
    points: List<MetricSample>,
    values: List<Double?>,
    rangeMinutes: Int,
    fixedMaximum: Double? = null,
    secondValues: List<Double?>? = null,
    secondLabel: String? = null,
    lineColor: Color = CpuSeriesColor,
    secondLineColor: Color = NetworkOutColor
) {
    val palette = LocalMonitorPalette.current
    val ordered = points.withIndex().sortedBy { it.value.epochMillis }
    val endTime = ordered.lastOrNull()?.value?.epochMillis ?: System.currentTimeMillis()
    val rangeMillis = rangeMinutes.coerceAtLeast(1) * 60_000L
    val startTime = endTime - rangeMillis
    val visible = ordered.filter { it.value.epochMillis >= startTime }
    val samples = visible.map { it.value }
    val visibleValues = visible.map { values.getOrNull(it.index) }
    val visibleSecondValues = secondValues?.let { series -> visible.map { series.getOrNull(it.index) } }
    val allValues = visibleValues + (visibleSecondValues ?: emptyList())
    val maxValue = fixedMaximum ?: dynamicMaximum(allValues)
    var selectedEpochMillis by remember { mutableStateOf<Long?>(null) }
    val selectedIndex = selectedEpochMillis?.let { selectedAt ->
        samples.indices.minByOrNull { abs(samples[it].epochMillis - selectedAt) }
    } ?: samples.lastIndex.takeIf { it >= 0 }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(if (fixedMaximum != null) "0–100 $unit" else "0–${formatAxis(maxValue, unit)}", color = palette.muted, fontSize = 11.sp)
        }
        if (samples.isEmpty() || allValues.none { it != null }) {
            Text("Нет данных", color = palette.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp))
        } else {
            if (visibleSecondValues == null) {
                SeriesStats(points, values, unit = unit, color = lineColor)
            } else {
                SeriesStats(points, values, unit = unit, label = "ВХОДЯЩАЯ", color = lineColor)
                SeriesStats(points, secondValues.orEmpty(), unit = unit, label = "ИСХОДЯЩАЯ", color = secondLineColor)
            }
            Canvas(Modifier.fillMaxWidth().height(112.dp).padding(top = 10.dp)) {
                val left = 31.dp.toPx()
                val right = size.width - 4.dp.toPx()
                val top = 5.dp.toPx()
                val bottom = size.height - 4.dp.toPx()
                val chartWidth = (right - left).coerceAtLeast(1f)
                val chartHeight = (bottom - top).coerceAtLeast(1f)
                for (line in 0..4) {
                    val y = top + chartHeight * line / 4f
                    drawLine(palette.grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                }
                for (line in 0..4) {
                    val fraction = 1f - line / 4f
                    val label = if (fixedMaximum != null) "${(fraction * 100).toInt()}" else formatAxis(maxValue * fraction, unit)
                    drawContext.canvas.nativeCanvas.drawText(label, 0f, top + chartHeight * line / 4f + 4.dp.toPx(),
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = palette.muted.toArgb()
                            textSize = 9.sp.toPx()
                        })
                }
                val span = rangeMillis.toFloat().coerceAtLeast(1f)
                fun x(index: Int): Float = left + chartWidth * ((samples[index].epochMillis - startTime) / span)
                fun drawSeries(series: List<Double?>, color: Color) {
                    val displayValues = smoothForDisplay(series, maxValue)
                    var run = mutableListOf<Offset>()
                    fun drawRun() {
                        if (run.isEmpty()) return
                        val path = Path().apply { moveTo(run.first().x, run.first().y) }
                        for (i in 0 until run.lastIndex) {
                            val start = run[i]
                            val end = run[i + 1]
                            val previous = run.getOrNull(i - 1) ?: start
                            val next = run.getOrNull(i + 2) ?: end
                            val dx = end.x - start.x
                            val beforeDx = (end.x - previous.x).coerceAtLeast(1f)
                            val afterDx = (next.x - start.x).coerceAtLeast(1f)
                            val lowY = minOf(start.y, end.y)
                            val highY = maxOf(start.y, end.y)
                            val control1Y = (start.y + ((end.y - previous.y) / beforeDx) * dx / 3f).coerceIn(lowY, highY)
                            val control2Y = (end.y - ((next.y - start.y) / afterDx) * dx / 3f).coerceIn(lowY, highY)
                            path.cubicTo(start.x + dx / 3f, control1Y, end.x - dx / 3f, control2Y, end.x, end.y)
                        }
                        drawPath(path, color, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        if (run.size == 1) drawCircle(color, radius = 2.dp.toPx(), center = run.first())
                        run.lastOrNull()?.let { drawCircle(color, radius = 2.5.dp.toPx(), center = it) }
                        run = mutableListOf()
                    }
                    displayValues.forEachIndexed { index, value ->
                        val normalized = value?.let { (it / maxValue).coerceIn(0.0, 1.0) }
                        if (normalized == null) {
                            drawRun()
                        } else {
                            run += Offset(x(index), bottom - chartHeight * normalized.toFloat())
                        }
                    }
                    drawRun()
                    val chosenIndex = selectedIndex ?: -1
                    val rawValue = series.getOrNull(chosenIndex)
                    if (chosenIndex in samples.indices && rawValue != null) {
                        val y = bottom - chartHeight * (rawValue / maxValue).coerceIn(0.0, 1.0).toFloat()
                        drawCircle(color, radius = 4.dp.toPx(), center = Offset(x(chosenIndex), y))
                    }
                }
                val chosenIndex = selectedIndex ?: -1
                if (chosenIndex in samples.indices) {
                    val selectedX = x(chosenIndex)
                    drawLine(palette.muted, Offset(selectedX, top), Offset(selectedX, bottom), strokeWidth = 1.dp.toPx())
                }
                drawSeries(visibleValues, lineColor)
                visibleSecondValues?.let { drawSeries(it, secondLineColor) }
            }
            val chosenIndex = selectedIndex ?: -1
            if (chosenIndex in samples.indices) {
                val detail = buildString {
                    append(TimeFormat.format(Instant.ofEpochMilli(samples[chosenIndex].epochMillis)))
                    append("  ·  ").append(title).append(" ").append(formatMetric(visibleValues.getOrNull(chosenIndex), unit))
                    visibleSecondValues?.let { append("  ·  OUT ").append(formatMetric(it.getOrNull(chosenIndex), unit)) }
                }
                Text(detail, color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 31.dp, top = 5.dp))
            }
            Row(Modifier.fillMaxWidth().padding(start = 31.dp, top = 2.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(TimeFormat.format(Instant.ofEpochMilli(startTime)), color = palette.muted, fontSize = 10.sp)
                Text(TimeFormat.format(Instant.ofEpochMilli(startTime + rangeMillis / 2)), color = palette.muted, fontSize = 10.sp)
                Text(TimeFormat.format(Instant.ofEpochMilli(endTime)), color = palette.muted, fontSize = 10.sp)
            }
            val selectedSample = samples.getOrNull(selectedIndex ?: -1)
            val sliderStartTime = samples.firstOrNull()?.epochMillis ?: startTime
            val sliderEndTime = samples.lastOrNull()?.epochMillis ?: endTime
            val sliderTimeSpan = (sliderEndTime - sliderStartTime).coerceAtLeast(1L)
            val sliderPosition = if (samples.size < 2) 1f else selectedSample?.let {
                ((it.epochMillis - sliderStartTime).toDouble() / sliderTimeSpan.toDouble()).toFloat().coerceIn(0f, 1f)
            } ?: 1f
            ChartScrubber(
                position = sliderPosition,
                enabled = samples.size > 1,
                firstTime = samples.firstOrNull()?.epochMillis?.let { TimeFormat.format(Instant.ofEpochMilli(it)) } ?: "—",
                lastTime = samples.lastOrNull()?.epochMillis?.let { TimeFormat.format(Instant.ofEpochMilli(it)) } ?: "—",
                onPositionChange = { fraction ->
                    val target = sliderStartTime + (fraction.toDouble() * sliderTimeSpan.toDouble()).toLong()
                    selectedEpochMillis = samples.indices.minByOrNull { abs(samples[it].epochMillis - target) }
                        ?.let { samples[it].epochMillis }
                }
            )
            if (secondValues != null) {
                Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(color = lineColor, label = "↓ Входящая")
                    Legend(color = secondLineColor, label = "↑ Исходящая")
                }
            }
        }
    }
}

@Composable
private fun ChartScrubber(
    position: Float,
    enabled: Boolean,
    firstTime: String,
    lastTime: String,
    onPositionChange: (Float) -> Unit
) {
    val palette = LocalMonitorPalette.current
    val density = LocalDensity.current
    val inset = with(density) { 11.dp.toPx() }
    val trackColor = if (enabled) palette.accent else palette.muted
    fun positionAt(x: Float, width: Float): Float =
        ((x - inset) / (width - inset * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)

    Canvas(
        Modifier.fillMaxWidth()
            .height(38.dp)
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { point -> onPositionChange(positionAt(point.x, size.width.toFloat())) }
            }
            .pointerInput(enabled) {
                if (enabled) detectDragGestures(
                    onDragStart = { point -> onPositionChange(positionAt(point.x, size.width.toFloat())) },
                    onDrag = { change, _ ->
                        change.consume()
                        onPositionChange(positionAt(change.position.x, size.width.toFloat()))
                    }
                )
            }
    ) {
        val left = inset
        val right = size.width - inset
        val centerY = size.height / 2f
        val thumbX = left + (right - left) * position.coerceIn(0f, 1f)
        drawLine(palette.grid, Offset(left, centerY), Offset(right, centerY), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(trackColor, Offset(left, centerY), Offset(thumbX, centerY), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        for (tick in 0..4) {
            val tickX = left + (right - left) * tick / 4f
            drawCircle(palette.card.copy(alpha = 0.75f), radius = 1.dp.toPx(), center = Offset(tickX, centerY))
        }
        drawCircle(trackColor.copy(alpha = 0.15f), radius = 13.dp.toPx(), center = Offset(thumbX, centerY))
        drawCircle(palette.card, radius = 9.dp.toPx(), center = Offset(thumbX, centerY))
        drawCircle(trackColor, radius = 6.dp.toPx(), center = Offset(thumbX, centerY))
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(firstTime, color = palette.muted, fontSize = 9.sp)
        Text(lastTime, color = palette.muted, fontSize = 9.sp)
    }
}

@Composable
private fun SeriesStats(points: List<MetricSample>, values: List<Double?>, unit: String, label: String? = null, color: Color = CpuSeriesColor) {
    val palette = LocalMonitorPalette.current
    val present = values.filterNotNull()
    val average = present.takeIf { it.isNotEmpty() }?.average()
    val maximumIndex = values.indices.filter { values[it] != null }
        .maxWithOrNull(compareBy<Int> { values[it]!! }.thenBy { points.getOrNull(it)?.epochMillis ?: 0L })
    val maximumTime = maximumIndex?.let { points.getOrNull(it)?.epochMillis }
        ?.let { TimeFormat.format(Instant.ofEpochMilli(it)) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (label != null) Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
        StatValue("СРЕДНЕЕ", formatMetric(average, unit), Modifier.weight(1f))
        StatValue("МИН", formatMetric(present.minOrNull(), unit), Modifier.weight(0.75f))
        StatValue("МАКС", formatMetric(present.maxOrNull(), unit), Modifier.weight(0.75f), maximumTime)
    }
}

@Composable
private fun StatValue(label: String, value: String, modifier: Modifier, detail: String? = null) {
    val palette = LocalMonitorPalette.current
    Column(modifier) {
        Text(label, color = palette.muted, fontSize = 7.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(value, color = Color(0xFF253149), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        detail?.let { Text(it, color = palette.muted, fontSize = 8.sp, maxLines = 1) }
    }
}

private fun formatMetric(value: Double?, unit: String): String = when {
    value == null -> "—"
    unit == "%" -> String.format(Locale.getDefault(), "%.1f%%", value)
    unit == "Б/с" -> formatRate(value)
    else -> String.format(Locale.getDefault(), "%.1f %s", value, unit)
}

/** Smooth only the visual series; the in-memory and API samples remain untouched. Nulls split the line into separate runs. */
private fun smoothForDisplay(values: List<Double?>, scale: Double): List<Double?> {
    val result = MutableList<Double?>(values.size) { null }
    var start = 0
    while (start < values.size) {
        if (values[start] == null) {
            start++
            continue
        }
        var end = start
        while (end + 1 < values.size && values[end + 1] != null) end++
        for (index in start..end) {
            var weightedTotal = 0.0
            var weightTotal = 0.0
            for (near in maxOf(start, index - 2)..minOf(end, index + 2)) {
                val value = values[near] ?: continue
                val weight = (3 - kotlin.math.abs(near - index)).toDouble()
                weightedTotal += value * weight
                weightTotal += weight
            }
            result[index] = if (weightTotal == 0.0) values[index] else weightedTotal / weightTotal
        }
        result[start] = values[start]
        result[end] = values[end]
        val preserveDeviation = maxOf(scale * 0.06, 0.5)
        for (index in (start + 1) until end) {
            val raw = values[index] ?: continue
            val before = values[index - 1] ?: continue
            val after = values[index + 1] ?: continue
            val isPeak = raw > before && raw > after
            val isDip = raw < before && raw < after
            if ((isPeak || isDip) && abs(raw - (result[index] ?: raw)) >= preserveDeviation) result[index] = raw
        }
        start = end + 1
    }
    return result
}

@Composable
private fun Legend(color: Color, label: String) {
    val palette = LocalMonitorPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.padding(top = 5.dp).height(6.dp).fillMaxWidth(0.04f)) { drawCircle(color) }
        Text(label, color = palette.muted, fontSize = 10.sp)
    }
}

private fun dynamicMaximum(values: List<Double?>): Double {
    val max = values.filterNotNull().maxOrNull() ?: 1.0
    if (max <= 0.0) return 1.0
    val step = when {
        max <= 10 -> 2.0
        max <= 50 -> 10.0
        max <= 100 -> 20.0
        max <= 500 -> 100.0
        else -> 1.0
    }
    return ceil(max / step) * step
}

private fun formatAxis(value: Double, unit: String): String =
    if (unit == "Б/с") formatRate(value) else "${value.toInt()} $unit"

fun formatRate(value: Double): String = when {
    value >= 1024.0 * 1024.0 -> "${"%.1f".format(value / (1024.0 * 1024.0))} МБ/с"
    value >= 1024.0 -> "${"%.1f".format(value / 1024.0)} КБ/с"
    else -> "${value.toInt()} Б/с"
}

