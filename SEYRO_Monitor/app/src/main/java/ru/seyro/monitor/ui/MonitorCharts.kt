package ru.seyro.monitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.seyro.monitor.data.MetricSample
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    fixedMaximum: Double? = null,
    secondValues: List<Double?>? = null,
    secondLabel: String? = null
) {
    val samples = points.sortedBy { it.epochMillis }
    val allValues = values + (secondValues ?: emptyList())
    val maxValue = fixedMaximum ?: dynamicMaximum(allValues)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color(0xFF253149), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(if (fixedMaximum != null) "0–100 $unit" else "0–${formatAxis(maxValue, unit)}", color = Muted, fontSize = 11.sp)
        }
        if (samples.isEmpty() || allValues.none { it != null }) {
            Text("Нет данных", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp))
        } else {
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
                val firstTime = samples.first().epochMillis
                val span = (samples.last().epochMillis - firstTime).coerceAtLeast(1L).toFloat()
                fun x(index: Int): Float = left + chartWidth * ((samples[index].epochMillis - firstTime) / span)
                fun drawSeries(series: List<Double?>, color: Color) {
                    val path = Path()
                    var segmentOpen = false
                    series.forEachIndexed { index, value ->
                        val normalized = value?.let { (it / maxValue).coerceIn(0.0, 1.0) }
                        if (normalized == null) {
                            segmentOpen = false
                        } else {
                            val point = Offset(x(index), bottom - chartHeight * normalized.toFloat())
                            if (segmentOpen) path.lineTo(point.x, point.y) else path.moveTo(point.x, point.y)
                            segmentOpen = true
                            drawCircle(color, radius = 2.4.dp.toPx(), center = point)
                        }
                    }
                    drawPath(path, color, style = Stroke(width = 2.2.dp.toPx()))
                }
                drawSeries(values, Blue)
                secondValues?.let { drawSeries(it, Violet) }
            }
            Row(Modifier.fillMaxWidth().padding(start = 31.dp, top = 2.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(samples.firstOrNull()?.let { TimeFormat.format(Instant.ofEpochMilli(it.epochMillis)) } ?: "—", color = Muted, fontSize = 10.sp)
                Text(samples.getOrNull(samples.size / 2)?.let { TimeFormat.format(Instant.ofEpochMilli(it.epochMillis)) } ?: "—", color = Muted, fontSize = 10.sp)
                Text(samples.lastOrNull()?.let { TimeFormat.format(Instant.ofEpochMilli(it.epochMillis)) } ?: "—", color = Muted, fontSize = 10.sp)
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
