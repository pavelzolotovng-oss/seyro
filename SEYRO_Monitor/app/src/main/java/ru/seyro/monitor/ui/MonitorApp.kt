package ru.seyro.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import ru.seyro.monitor.BuildConfig
import ru.seyro.monitor.data.MonitorApi
import ru.seyro.monitor.data.MonitorConfig
import ru.seyro.monitor.data.MonitorConfigStore
import ru.seyro.monitor.data.MonitorNode
import ru.seyro.monitor.data.MonitorSnapshot
import ru.seyro.monitor.data.NodeHealth
import ru.seyro.monitor.data.NodeMetrics
import ru.seyro.monitor.data.NodeRole
import ru.seyro.monitor.update.UpdateCheck
import ru.seyro.monitor.update.UpdateDownloader
import ru.seyro.monitor.update.UpdateInfo
import ru.seyro.monitor.update.UpdateRepository
import java.util.Locale

private enum class Screen { OVERVIEW, NODE_DETAIL, SETTINGS }

@Composable
fun MonitorApp() {
    val context = LocalContext.current
    val configStore = remember(context) { MonitorConfigStore(context) }
    val config by configStore.config.collectAsState(initial = MonitorConfig("https://admin.seyro.ru", ""))
    val api = remember { MonitorApi() }
    val scope = rememberCoroutineScope()
    val refreshMutex = remember { Mutex() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var appVisible by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var screen by remember { mutableStateOf(Screen.OVERVIEW) }
    var selectedNode by remember { mutableStateOf<MonitorNode?>(null) }
    var detailNode by remember { mutableStateOf<MonitorNode?>(null) }
    var snapshot by remember { mutableStateOf<MonitorSnapshot?>(null) }
    var metricHistory by remember { mutableStateOf<Map<String, List<NodeMetrics>>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { source, _ ->
            appVisible = source.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    suspend fun refreshCurrent() {
        if (config.token.isBlank() || screen == Screen.SETTINGS || !refreshMutex.tryLock()) return
        loading = true
        connectionError = null
        try {
            when (screen) {
                Screen.OVERVIEW -> snapshot = api.load(config)
                Screen.NODE_DETAIL -> selectedNode?.let { node ->
                    val updated = api.loadNode(config, node.role, node.id)
                    detailNode = updated
                    metricHistory = appendSample(metricHistory, nodeHistoryKey(node), updated.metrics)
                }
                Screen.SETTINGS -> Unit
            }
        } catch (error: Exception) {
            connectionError = error.message ?: "Не удалось получить данные сервера"
        } finally {
            loading = false
            refreshMutex.unlock()
        }
    }

    LaunchedEffect(screen, selectedNode?.id, config.baseUrl, config.token, appVisible) {
        if (!appVisible || config.token.isBlank() || screen == Screen.SETTINGS) return@LaunchedEffect
        while (true) {
            refreshCurrent()
            delay(15_000)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppBar() },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = screen == Screen.OVERVIEW || screen == Screen.NODE_DETAIL,
                    onClick = { screen = Screen.OVERVIEW },
                    icon = { Text("◉") },
                    label = { Text("Обзор") }
                )
                NavigationBarItem(
                    selected = screen == Screen.SETTINGS,
                    onClick = { screen = Screen.SETTINGS },
                    icon = { Text("⚙") },
                    label = { Text("Доступ") }
                )
            }
        }
    ) { padding ->
        when (screen) {
            Screen.OVERVIEW -> Overview(
                modifier = Modifier.padding(padding),
                snapshot = snapshot,
                loading = loading,
                error = connectionError,
                configured = config.token.isNotBlank(),
                onRefresh = { scope.launch { refreshCurrent() } },
                onOpenSettings = { screen = Screen.SETTINGS },
                onSelectNode = { node ->
                    selectedNode = node
                    detailNode = node
                    metricHistory = appendSample(metricHistory, nodeHistoryKey(node), node.metrics)
                    screen = Screen.NODE_DETAIL
                }
            )
            Screen.NODE_DETAIL -> NodeDetail(
                modifier = Modifier.padding(padding),
                node = detailNode,
                history = selectedNode?.let { metricHistory[nodeHistoryKey(it)] }.orEmpty(),
                loading = loading,
                error = connectionError,
                onBack = { screen = Screen.OVERVIEW }
            )
            Screen.SETTINGS -> Settings(
                modifier = Modifier.padding(padding),
                config = config,
                configStore = configStore,
                api = api,
                onConnected = { result ->
                    snapshot = result
                    connectionError = null
                },
                onConnectionError = { message -> connectionError = message }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar() = TopAppBar(title = {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(40.dp).background(Color(0xFF143A35), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) { Text("S", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge) }
        Column {
            Text("SEYRO", style = MaterialTheme.typography.titleMedium)
            Text("MONITORING CONSOLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
})

@Composable
private fun Overview(
    modifier: Modifier,
    snapshot: MonitorSnapshot?,
    loading: Boolean,
    error: String?,
    configured: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectNode: (MonitorNode) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (snapshot == null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (configured) "СВЯЗЬ НЕ УСТАНОВЛЕНА" else "ДОБРО ПОЖАЛОВАТЬ В SEYRO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(if (configured) "Нет данных от сервера" else "Подключите мониторинг", style = MaterialTheme.typography.headlineSmall)
                        Text(error ?: "Адрес сервера уже указан. Введите выпущенный monitor-token в разделе «Доступ».")
                        if (!configured) Button(onClick = onOpenSettings) { Text("Настроить доступ") }
                        else Button(onClick = onRefresh, enabled = !loading) { Text(if (loading) "Подключаемся…" else "Повторить") }
                    }
                }
            }
            return@LazyColumn
        }

        val overview = snapshot.overview
        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10221F))
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("СИСТЕМА", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Обзор сети", style = MaterialTheme.typography.headlineSmall)
                        }
                        StatusPill(if (overview.nodesOffline == 0 && overview.nodesXrayStopped == 0 && overview.nodesTotal > 0) "НОРМА" else "ВНИМАНИЕ", overview.nodesOffline == 0 && overview.nodesXrayStopped == 0 && overview.nodesTotal > 0)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SummaryTile("УЗЛЫ", "${overview.nodesOnline}/${overview.nodesTotal}", "${overview.nodesOffline} офлайн", Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                        SummaryTile("ПОЛЬЗОВАТЕЛИ", overview.onlineUsers?.toString() ?: "—", if (overview.onlineUsersStale) "данные устарели" else "сейчас онлайн", Modifier.weight(1f), Color(0xFF79BFFF))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SummaryTile("IN · ВХОД", "${overview.activeInNodes}", "активных узлов", Modifier.weight(1f), Color(0xFFB6A0FF))
                        SummaryTile("OUT · ВЫХОД", "${overview.activeOutNodes}", "активных узлов", Modifier.weight(1f), Color(0xFFF0B66D))
                    }
                    HorizontalDivider()
                    Text(
                        if (overview.onlineUsersStale) "Данные онлайн-пользователей устарели · ${prettyTime(overview.onlineUsersSampledAt)}"
                        else "Замер онлайн-пользователей: ${prettyTime(overview.onlineUsersSampledAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overview.onlineUsersStale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("API ${snapshot.system.apiStatus} · БД ${snapshot.system.databaseStatus}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (loading) "ОБНОВЛЕНИЕ…" else "LIVE · 15 СЕК", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Button(onClick = onRefresh, enabled = !loading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(if (loading) "Обновляем…" else "Обновить данные") }
                    if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("ИНФРАСТРУКТУРА", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Серверы", style = MaterialTheme.typography.headlineSmall)
                }
                Text("${snapshot.nodes.size} УЗЛА", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (snapshot.nodes.isEmpty()) {
            item { Text("В API пока нет узлов.") }
        } else {
            items(snapshot.nodes, key = { "${it.role}-${it.id}" }) { node ->
                NodeCard(node = node, onDetails = { onSelectNode(node) })
            }
        }
    }
}

@Composable
private fun NodeCard(node: MonitorNode, onDetails: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (node.role == NodeRole.IN) "INCOMING NODE" else "OUTGOING NODE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(node.name, style = MaterialTheme.typography.titleLarge)
                }
                StatusPill(healthLabel(node.health).uppercase(), node.health == NodeHealth.ONLINE)
            }
            val location = listOfNotNull(node.city, node.country).joinToString(", ")
            Text(listOfNotNull(location.takeIf { it.isNotBlank() }, "ID ${node.id}").joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("XRAY", node.xrayActive?.let { if (it) "АКТИВЕН" else "ОСТАНОВЛЕН" } ?: "НЕТ ДАННЫХ", node.xrayActive == true)
                if (node.syncPending) InfoPill("SYNC", "ОЖИДАНИЕ", false)
            }
            MetricsPanel(node.metrics, available = node.metricsAvailable, stale = node.metricsStale)
            Text("СИГНАЛ · ${prettyTime(node.lastSeenAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onDetails, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2A35))) { Text("Подробнее  →", color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun MetricsPanel(metrics: NodeMetrics?, available: Boolean, stale: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("РЕСУРСЫ", style = MaterialTheme.typography.labelMedium)
                if (available) Text(if (stale) "УСТАРЕЛИ" else "АКТУАЛЬНЫ", style = MaterialTheme.typography.labelSmall, color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            if (!available || metrics == null) {
                Text("Замер ещё не получен")
                return@Column
            }
            MetricLine("CPU", metrics.cpuPercent?.let(::percent) ?: "—", metrics.cpuPercent?.div(100.0)?.toFloat(), Color(0xFF72E5C2))
            MetricLine("RAM", "${formatBytes(metrics.ramUsedBytes)} / ${formatBytes(metrics.ramTotalBytes)}", fraction(metrics.ramUsedBytes, metrics.ramTotalBytes), Color(0xFF79BFFF))
            MetricLine("ДИСК", "${formatBytes(metrics.diskUsedBytes)} / ${formatBytes(metrics.diskTotalBytes)}", fraction(metrics.diskUsedBytes, metrics.diskTotalBytes), Color(0xFFB6A0FF))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("↓ ${metrics.networkRxBytesPerSec?.let(::formatRate) ?: "—"}", style = MaterialTheme.typography.bodySmall)
                Text("↑ ${metrics.networkTxBytesPerSec?.let(::formatRate) ?: "—"}", style = MaterialTheme.typography.bodySmall)
                Text(formatUptime(metrics.uptimeSeconds), style = MaterialTheme.typography.bodySmall)
            }
            Text("ЗАМЕР · ${prettyTime(metrics.sampledAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, caption: String, modifier: Modifier, accent: Color) {
    Column(modifier.background(Color(0xFF172B27), RoundedCornerShape(17.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusPill(label: String, healthy: Boolean) {
    val tint = if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(shape = RoundedCornerShape(50), color = tint.copy(alpha = 0.12f), modifier = Modifier.border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(50))) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun InfoPill(label: String, value: String, healthy: Boolean) {
    val tint = if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text("$label  ·  $value", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun MetricLine(label: String, value: String, fraction: Float?, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(
            progress = { (fraction ?: 0f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = tint,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    }
}

private fun fraction(used: Long, total: Long): Float? = if (total > 0) used.toFloat() / total else null

@Composable
private fun NodeDetail(
    modifier: Modifier,
    node: MonitorNode?,
    history: List<NodeMetrics>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) { Text("←  К серверам", color = MaterialTheme.colorScheme.onSurface) }
        }
        if (node == null) {
            item { Text(error ?: if (loading) "Загрузка узла…" else "Не удалось загрузить узел") }
        } else {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(if (node.role == NodeRole.IN) "INCOMING NODE · ${node.id}" else "OUTGOING NODE · ${node.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(node.name, style = MaterialTheme.typography.headlineSmall)
                        StatusPill(healthLabel(node.health).uppercase(), node.health == NodeHealth.ONLINE)
                        Text("Конфигурация · ${node.configuredStatus}", style = MaterialTheme.typography.bodySmall)
                        Text("Xray · ${node.xrayActive?.let { if (it) "работает" else "остановлен" } ?: "нет данных"}")
                        Text("Транспорты · ${node.enabledTransports.takeIf { it.isNotEmpty() }?.joinToString() ?: "—"}", style = MaterialTheme.typography.bodySmall)
                        Text("Последний сигнал · ${prettyTime(node.lastSeenAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        node.syncMessage?.let { Text("Синхронизация · $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { MetricsPanel(node.metrics, node.metricsAvailable, node.metricsStale) }
            item { MetricHistory(history) }
            item {
                Text(
                    if (loading) "Получаем свежий замер…" else "Автообновление · каждые 15 секунд",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private data class ChartLine(val name: String, val color: Color, val values: List<Float>)

@Composable
private fun MetricHistory(history: List<NodeMetrics>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ИСТОРИЯ ПОКАЗАТЕЛЕЙ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Последние замеры", style = MaterialTheme.typography.titleLarge)
        }
        if (history.isEmpty()) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text("История появится после первого замера от сервера.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        val cpu = history.mapNotNull { it.cpuPercent?.toFloat() }
        val ram = history.map { fraction(it.ramUsedBytes, it.ramTotalBytes)?.times(100f) ?: 0f }
        val disk = history.map { fraction(it.diskUsedBytes, it.diskTotalBytes)?.times(100f) ?: 0f }
        val rx = history.mapNotNull { it.networkRxBytesPerSec?.toFloat()?.div(1_048_576f) }
        val tx = history.mapNotNull { it.networkTxBytesPerSec?.toFloat()?.div(1_048_576f) }

        HistoryChart("CPU", listOf(ChartLine("Загрузка", Color(0xFF72E5C2), cpu)), "%", fixedMax = 100f)
        HistoryChart(
            "Память и диск",
            listOf(ChartLine("RAM", Color(0xFF79BFFF), ram), ChartLine("Диск", Color(0xFFB6A0FF), disk)),
            "%",
            fixedMax = 100f
        )
        HistoryChart(
            "Сеть",
            listOf(ChartLine("↓ Вход", Color(0xFF72E5C2), rx), ChartLine("↑ Выход", Color(0xFFFFB86B), tx)),
            "МБ/с"
        )
        Text(
            "${prettyTime(history.first().sampledAt)}  →  ${prettyTime(history.last().sampledAt)} · ${history.size} замеров",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HistoryChart(title: String, lines: List<ChartLine>, unit: String, fixedMax: Float? = null) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.48f)
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                val gridStroke = 1.dp.toPx()
                for (index in 0..3) {
                    val y = size.height * index / 3f
                    drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), gridStroke)
                }
                val allValues = lines.flatMap { it.values }
                val maxValue = fixedMax ?: ((allValues.maxOrNull() ?: 0.01f) * 1.15f).coerceAtLeast(0.01f)
                lines.forEach { line ->
                    if (line.values.isEmpty()) return@forEach
                    val path = Path()
                    line.values.forEachIndexed { index, value ->
                        val x = if (line.values.size == 1) size.width / 2f else size.width * index / (line.values.lastIndex.toFloat())
                        val y = size.height - (value / maxValue).coerceIn(0f, 1f) * size.height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    if (line.values.size == 1) {
                        val y = size.height - (line.values.first() / maxValue).coerceIn(0f, 1f) * size.height
                        drawCircle(line.color, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width / 2f, y))
                    } else {
                        drawPath(path, line.color, style = Stroke(width = 3.dp.toPx()))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).background(line.color, RoundedCornerShape(50)))
                        Text(line.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (lines.all { it.values.size < 2 }) {
                Text("Для линии графика ждём следующий замер.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun nodeHistoryKey(node: MonitorNode): String = "${node.role}-${node.id}"

private fun appendSample(history: Map<String, List<NodeMetrics>>, key: String, sample: NodeMetrics?): Map<String, List<NodeMetrics>> {
    if (sample == null) return history
    val samples = history[key].orEmpty()
    if (samples.lastOrNull()?.sampledAt == sample.sampledAt) return history
    return history + (key to (samples + sample).takeLast(60))
}

@Composable
private fun Settings(
    modifier: Modifier,
    config: MonitorConfig,
    configStore: MonitorConfigStore,
    api: MonitorApi,
    onConnected: (MonitorSnapshot) -> Unit,
    onConnectionError: (String) -> Unit
) {
    var endpoint by remember(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf("Проверка ещё не запускалась") }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Подключение к серверу", style = MaterialTheme.typography.titleLarge) }
        if (config.token.isBlank()) {
            item { Text("Введите отдельный monitor-token с правами только на чтение. Токен будет сохранён в зашифрованном виде на этом устройстве.") }
            item {
                OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Base URL") }, placeholder = { Text("https://admin.seyro.ru") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Monitor token") },
                    placeholder = { Text("Вставьте выданный токен") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = {
                        val newConfig = MonitorConfig(endpoint, token.trim())
                        scope.launch {
                            busy = true
                            status = "Проверяем API…"
                            try {
                                val result = api.load(newConfig)
                                configStore.save(newConfig.baseUrl, newConfig.token)
                                token = ""
                                status = "Подключено. Получено серверов: ${result.nodes.size}."
                                onConnected(result)
                            } catch (error: Exception) {
                                val message = error.message ?: "Не удалось подключиться"
                                status = message
                                onConnectionError(message)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && endpoint.isNotBlank() && token.isNotBlank()
                ) { Text(if (busy) "Проверяем…" else "Сохранить и подключиться") }
            }
            if (status.isNotBlank()) item { Text(status, style = MaterialTheme.typography.bodySmall, color = if (status.startsWith("Подключено")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("ДОСТУП НАСТРОЕН", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(endpoint, style = MaterialTheme.typography.titleMedium)
                        Text("Ключ сохранён на устройстве и скрыт. Доступ только для просмотра.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Button(
                    onClick = { scope.launch { configStore.clear(); token = ""; status = "Ключ удалён с устройства." } },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Удалить ключ") }
            }
        }
        item { HorizontalDivider() }
        item { Text("Обновление приложения", style = MaterialTheme.typography.titleMedium) }
        item { Text(updateState, style = MaterialTheme.typography.bodySmall) }
        item {
            Button(onClick = {
                scope.launch {
                    updateState = "Проверяем GitHub…"
                    when (val result = UpdateRepository().check(BuildConfig.VERSION_CODE)) {
                        UpdateCheck.NoUpdate -> { availableUpdate = null; updateState = "Установлена актуальная версия" }
                        is UpdateCheck.Available -> { availableUpdate = result.update; updateState = "Доступна версия ${result.update.versionName}" }
                        is UpdateCheck.Failed -> { availableUpdate = null; updateState = result.message }
                    }
                }
            }) { Text("Проверить обновления") }
        }
        availableUpdate?.let { update ->
            item {
                Button(onClick = {
                    UpdateDownloader.start(context, update)
                    updateState = "Загрузка началась. После проверки SHA-256 Android предложит установку."
                }) { Text("Скачать ${update.versionName}") }
            }
        }
    }
}

private fun healthLabel(health: NodeHealth): String = when (health) {
    NodeHealth.ONLINE -> "Онлайн"
    NodeHealth.OFFLINE -> "Офлайн"
    NodeHealth.XRAY_STOPPED -> "Xray остановлен"
    NodeHealth.UNKNOWN -> "Нет данных"
    NodeHealth.DISABLED -> "Отключён"
}

private fun prettyTime(value: String?): String = value
    ?.replace('T', ' ')
    ?.removeSuffix("Z")
    ?.take(19)
    ?: "—"

private fun percent(value: Double): String = String.format(Locale.getDefault(), "%.1f%%", value)

private fun sharePercent(used: Long, total: Long): String =
    if (total > 0) " · ${percent(used.toDouble() * 100.0 / total)}" else ""

private fun formatRate(value: Double): String = "${formatBytes(value.toLong())}/с"

private fun formatBytes(value: Long): String {
    if (value < 1024) return "$value Б"
    val units = listOf("КБ", "МБ", "ГБ", "ТБ")
    var amount = value.toDouble()
    var unit = -1
    while (amount >= 1024 && unit < units.lastIndex) {
        amount /= 1024.0
        unit++
    }
    return String.format(Locale.getDefault(), "%.1f %s", amount, units[unit])
}

private fun formatUptime(value: Long): String {
    val days = value / 86_400
    val hours = (value % 86_400) / 3_600
    val minutes = (value % 3_600) / 60
    return if (days > 0) "$days д $hours ч" else "$hours ч $minutes мин"
}
