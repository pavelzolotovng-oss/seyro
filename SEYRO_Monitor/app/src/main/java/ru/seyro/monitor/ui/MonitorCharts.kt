package ru.seyro.monitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
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

private val Grid = Color(0xFFE7ECF4)
private val Muted = Color(0xFF8290A5)
private val Blue = Color(0xFF356AF4)
private val Violet = Color(0xFF9B6CF6)
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
    secondLabel: String? = null
) {
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
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color(0xFF253149), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(if (fixedMaximum != null) "0–100 $unit" else "0–${formatAxis(maxValue, unit)}", color = Muted, fontSize = 11.sp)
        }
        if (samples.isEmpty() || allValues.none { it != null }) {
            Text("Нет данных", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp))
        } else {
            if (visibleSecondValues == null) {
                SeriesStats(values = values, unit = unit)
            } else {
                SeriesStats(values = values, unit = unit, label = "ВХОДЯЩАЯ", color = Blue)
                SeriesStats(values = secondValues.orEmpty(), unit = unit, label = "ИСХОДЯЩАЯ", color = Violet)
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
                    drawLine(Grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                }
                for (line in 0..4) {
                    val fraction = 1f - line / 4f
                    val label = if (fixedMaximum != null) "${(fraction * 100).toInt()}" else formatAxis(maxValue * fraction, unit)
                    drawContext.canvas.nativeCanvas.drawText(label, 0f, top + chartHeight * line / 4f + 4.dp.toPx(),
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.rgb(130, 144, 165)
                            textSize = 9.sp.toPx()
                        })
                }
                val span = rangeMillis.toFloat().coerceAtLeast(1f)
                fun x(index: Int): Float = left + chartWidth * ((samples[index].epochMillis - startTime) / span)
                fun drawSeries(series: List<Double?>, color: Color) {
                    val displayValues = smoothForDisplay(series)
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
                }
                drawSeries(visibleValues, Blue)
                visibleSecondValues?.let { drawSeries(it, Violet) }
            }
            Row(Modifier.fillMaxWidth().padding(start = 31.dp, top = 2.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(TimeFormat.format(Instant.ofEpochMilli(startTime)), color = Muted, fontSize = 10.sp)
                Text(TimeFormat.format(Instant.ofEpochMilli(startTime + rangeMillis / 2)), color = Muted, fontSize = 10.sp)
                Text(TimeFormat.format(Instant.ofEpochMilli(endTime)), color = Muted, fontSize = 10.sp)
            }
            if (secondValues != null) {
                Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(color = Blue, label = "↓ Входящая")
                    Legend(color = Violet, label = "↑ Исходящая")
                }
            }
        }
    }
}

@Composable
private fun SeriesStats(values: List<Double?>, unit: String, label: String? = null, color: Color = Blue) {
    val present = values.filterNotNull()
    val average = present.takeIf { it.isNotEmpty() }?.average()
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (label != null) Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
        StatValue("СРЕДНЕЕ", formatMetric(average, unit), Modifier.weight(1f))
        StatValue("МИН", formatMetric(present.minOrNull(), unit), Modifier.weight(0.75f))
        StatValue("МАКС", formatMetric(present.maxOrNull(), unit), Modifier.weight(0.75f))
    }
}

@Composable
private fun StatValue(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 7.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(value, color = Color(0xFF253149), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun formatMetric(value: Double?, unit: String): String = when {
    value == null -> "—"
    unit == "%" -> String.format(Locale.getDefault(), "%.1f%%", value)
    unit == "Б/с" -> formatRate(value)
    else -> String.format(Locale.getDefault(), "%.1f %s", value, unit)
}

/** Smooth only the visual series; the in-memory and API samples remain untouched. Nulls split the line into separate runs. */
private fun smoothForDisplay(values: List<Double?>): List<Double?> {
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
        start = end + 1
    }
    return result
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.padding(top = 5.dp).height(6.dp).fillMaxWidth(0.04f)) { drawCircle(color) }
        Text(label, color = Muted, fontSize = 10.sp)
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
