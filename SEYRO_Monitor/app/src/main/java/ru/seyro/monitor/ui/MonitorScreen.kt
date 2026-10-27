package ru.seyro.monitor.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.seyro.monitor.BuildConfig
import ru.seyro.monitor.data.MetricSample
import ru.seyro.monitor.data.MonitorApi
import ru.seyro.monitor.data.MonitorConfig
import ru.seyro.monitor.data.MonitorConfigStore
import ru.seyro.monitor.data.OnlineOverview
import ru.seyro.monitor.data.ServerKey
import ru.seyro.monitor.data.ServerNode
import ru.seyro.monitor.update.UpdateInfo
import ru.seyro.monitor.update.UpdateRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

internal data class MonitorPalette(
    val page: Color, val card: Color, val text: Color, val muted: Color, val border: Color,
    val accent: Color, val green: Color, val red: Color, val amber: Color, val menuText: Color,
    val grid: Color, val softSurface: Color, val divider: Color, val errorSurface: Color,
    val goodSurface: Color, val goodText: Color, val roleInSurface: Color, val roleOutSurface: Color,
    val isDark: Boolean
)
private val LightMonitorPalette = MonitorPalette(
    page = Color(0xFFE9EEF5), card = Color(0xFFFFFFFF), text = Color(0xFF16243C),
    muted = Color(0xFF52627A), border = Color(0xFFCBD6E5), accent = Color(0xFF1767F5),
    green = Color(0xFF168965), red = Color(0xFFCF3948), amber = Color(0xFF9A6418),
    menuText = Color(0xFFE7EDF7), grid = Color(0xFFE1E7F0), softSurface = Color(0xFFF1F5FA),
    divider = Color(0xFFE1E7F0), errorSurface = Color(0xFFFFF0F0), goodSurface = Color(0xFFE8F7F1),
    goodText = Color(0xFF188966), roleInSurface = Color(0xFFEAF0FF), roleOutSurface = Color(0xFFF0EBFF),
    isDark = false
)
private val DarkMonitorPalette = MonitorPalette(
    page = Color(0xFF0B1220), card = Color(0xFF142238), text = Color(0xFFE7EDF7),
    muted = Color(0xFFA8B8CE), border = Color(0xFF33465F), accent = Color(0xFF1767F5),
    green = Color(0xFF4DD6A1), red = Color(0xFFFF7A86), amber = Color(0xFFF1BD63),
    menuText = Color(0xFFE7EDF7), grid = Color(0xFF33465F), softSurface = Color(0xFF1B2C44),
    divider = Color(0xFF33465F), errorSurface = Color(0xFF321E2A), goodSurface = Color(0xFF17392F),
    goodText = Color(0xFF72E0B7), roleInSurface = Color(0xFF1B2D4A), roleOutSurface = Color(0xFF2A2446),
    isDark = true
)
internal val LocalMonitorPalette = staticCompositionLocalOf { LightMonitorPalette }
private val Page: Color @Composable get() = LocalMonitorPalette.current.page
private val CardSurface: Color @Composable get() = LocalMonitorPalette.current.card
private val CardOutline: Color @Composable get() = LocalMonitorPalette.current.border
private val Ink: Color @Composable get() = LocalMonitorPalette.current.text
private val Muted: Color @Composable get() = LocalMonitorPalette.current.muted
private val Accent: Color @Composable get() = LocalMonitorPalette.current.accent
private val Green: Color @Composable get() = LocalMonitorPalette.current.green
private val Red: Color @Composable get() = LocalMonitorPalette.current.red
private val Amber: Color @Composable get() = LocalMonitorPalette.current.amber
private val SoftSurface: Color @Composable get() = LocalMonitorPalette.current.softSurface
private val DividerColor: Color @Composable get() = LocalMonitorPalette.current.divider
private val ErrorSurface: Color @Composable get() = LocalMonitorPalette.current.errorSurface
private val GoodSurface: Color @Composable get() = LocalMonitorPalette.current.goodSurface
private val GoodText: Color @Composable get() = LocalMonitorPalette.current.goodText
private val TimeLabel = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val MoscowDateTimeLabel = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Moscow"))

@Composable
fun MonitorScreen() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val configStore = remember { MonitorConfigStore(context) }
    val pollGate = remember { AtomicBoolean(false) }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val themePrefs = remember { context.getSharedPreferences("seyro_monitor_theme", android.content.Context.MODE_PRIVATE) }
    var isDarkTheme by remember { mutableStateOf(themePrefs.getBoolean("dark_theme", false)) }
    var config by remember { mutableStateOf(configStore.read()) }
    var nodes by remember { mutableStateOf<List<ServerNode>>(emptyList()) }
    var overview by remember { mutableStateOf<OnlineOverview?>(null) }
    var overviewExpanded by remember { mutableStateOf(false) }
    var lastUpdated by remember { mutableStateOf<Long?>(null) }
    var historyStartAt by remember { mutableStateOf(0L) }
    var statisticsClearedMessage by remember { mutableStateOf<String?>(null) }
    var selectedRangeMinutes by remember { mutableStateOf(15) }
    var selectedNodeKey by remember { mutableStateOf<ServerKey?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(config.token.isBlank()) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(config, lifecycleState) {
        if (config.token.isBlank() || !lifecycleState.isAtLeast(Lifecycle.State.STARTED)) return@LaunchedEffect
        var waitMillis = 15_000L
        while (isActive) {
            isLoading = nodes.isEmpty()
            try {
                val received = withContext(Dispatchers.IO) {
                    if (!pollGate.compareAndSet(false, true)) null
                    else try {
                        val serverNodes = MonitorApi.fetchNodes(config)
                        val online = runCatching { MonitorApi.fetchOverview(config) }.getOrNull()
                        serverNodes to online
                    } finally { pollGate.set(false) }
                }
                if (received == null) {
                    delay(1_000L)
                    continue
                }
                val now = System.currentTimeMillis()
                val cutoff = now - 30L * 60L * 1000L
                val old = nodes.associateBy { it.key }
                val (freshNodes, freshOverview) = received
                nodes = freshNodes.map { fresh ->
                    val oldSamples = old[fresh.key]?.samples.orEmpty()
                    val byTimestamp = LinkedHashMap<String, MetricSample>()
                    (oldSamples + fresh.samples).forEach {
                        if (it.epochMillis >= cutoff && it.epochMillis >= historyStartAt) byTimestamp[it.sampledAt] = it
                    }
                    fresh.copy(samples = byTimestamp.values.sortedBy { it.epochMillis })
                }
                if (freshOverview != null) overview = freshOverview
                else overview = overview?.copy(stale = true)
                lastUpdated = now
                loadError = null
                isLoading = false
                waitMillis = 15_000L
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                isLoading = false
                loadError = error.message ?: "Не удалось получить данные"
                waitMillis = (waitMillis * 2).coerceAtMost(5L * 60L * 1000L)
            }
            delay(waitMillis)
        }
    }

    val selectedNode = nodes.firstOrNull { it.key == selectedNodeKey }

    CompositionLocalProvider(LocalMonitorPalette provides if (isDarkTheme) DarkMonitorPalette else LightMonitorPalette) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Page) {
            if (selectedNode != null) {
                NodeDetailScreen(
                    node = selectedNode,
                    rangeMinutes = selectedRangeMinutes,
                    onRangeSelected = { selectedRangeMinutes = it },
                    onBack = { selectedNodeKey = null }
                )
            } else Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = (screenHeightDp * 0.05f).dp)) {
                Header(isDarkTheme = isDarkTheme, onToggleTheme = {
                    isDarkTheme = !isDarkTheme
                    themePrefs.edit().putBoolean("dark_theme", isDarkTheme).apply()
                }, onSettings = { showSettings = true })
                if (config.token.isNotBlank()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { OverviewCard(nodes, lastUpdated, overview, overviewExpanded) { overviewExpanded = !overviewExpanded } }
                        if (loadError != null) {
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = ErrorSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text("Нет связи: $loadError · повторим автоматически", color = Red, fontSize = 12.sp, modifier = Modifier.padding(13.dp))
                                }
                            }
                        }
                        if (isLoading && nodes.isEmpty()) {
                            item {
                                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                                    Text("Загружаем узлы…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        } else if (nodes.isEmpty() && loadError == null) {
                            item { EmptyState("API пока не вернул узлы") }
                        } else if (nodes.isNotEmpty() && !overviewExpanded) {
                            item { SectionHeader(nodes.size) }
                            items(nodes, key = { "${it.key.role}:${it.key.id}" }) { node ->
                                NodeCard(node) { selectedNodeKey = node.key }
                            }
                            item { Footer() }
                        }
                    }
                } else {
                    EmptyState("Добавьте Monitor Bearer-токен, чтобы подключиться к API")
                }
            }
        }
    }

    if (showSettings) {
        AccessAndSettingsDialog(
            config = config,
            updateMessage = updateMessage,
            updateInfo = updateInfo,
            checkingUpdate = checkingUpdate,
            downloadingUpdate = downloadingUpdate,
            updateError = updateError,
            onDismiss = { showSettings = false },
            onSave = { next ->
                configStore.save(next)
                config = next.copy(apiBase = next.apiBase.trim().trimEnd('/'), token = next.token.trim())
                nodes = emptyList()
                overview = null
                lastUpdated = null
                historyStartAt = 0L
                statisticsClearedMessage = null
                loadError = null
                showSettings = false
            },
            onDeleteAccess = {
                configStore.clear()
                config = MonitorConfig("https://admin.seyro.ru", "")
                nodes = emptyList()
                overview = null
                lastUpdated = null
                historyStartAt = 0L
                statisticsClearedMessage = null
                showSettings = true
            },
            onClearStatistics = {
                historyStartAt = System.currentTimeMillis()
                nodes = nodes.map { it.copy(samples = emptyList()) }
                statisticsClearedMessage = "Локальная история удалена. Новые точки появятся после следующего замера."
            },
            statisticsClearedMessage = statisticsClearedMessage,
            onCheckUpdate = {
                checkingUpdate = true
                updateMessage = "Проверяем GitHub Releases…"
                updateInfo = null
                updateError = null
                try {
                    val found = withContext(Dispatchers.IO) { UpdateRepository.check(BuildConfig.VERSION_CODE) }
                    updateInfo = found
                    updateMessage = if (found == null) "Установлена актуальная версия ${BuildConfig.VERSION_NAME}" else "Доступно обновление ${found.versionName}"
                } catch (error: Exception) {
                    updateMessage = "Не удалось проверить обновления"
                    updateError = error.message ?: "Ошибка сети"
                } finally {
                    checkingUpdate = false
                }
            },
            onDownloadUpdate = {
                if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                    context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                } else {
                    downloadingUpdate = true
                    updateError = null
                    try {
                        val apk = withContext(Dispatchers.IO) { UpdateRepository.download(context, updateInfo!!) }
                        val uri = UpdateRepository.apkUri(context, apk)
                        val install = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ContextCompat.startActivity(context, install, null)
                    } catch (error: Exception) {
                        updateError = error.message ?: "Не удалось скачать APK"
                    } finally {
                        downloadingUpdate = false
                    }
                }
            }
        )
    }
    }
}

@Composable
private fun Header(isDarkTheme: Boolean, onToggleTheme: () -> Unit, onSettings: () -> Unit) {
    Surface(color = Color(0xFF102338), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Accent), contentAlignment = Alignment.Center) {
            Text("S", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text("SEYRO", color = Color(0xFFE7EDF7), fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Text("MONITORING", color = Color(0xFFA8B8CE), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.1.sp)
        }
        Surface(color = GoodSurface, shape = CircleShape) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Green))
                Text(" LIVE", color = GoodText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(6.dp))
        Surface(color = Color(0xFF1B344E), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp).clickable(onClick = onToggleTheme)) {
            Box(contentAlignment = Alignment.Center) { Text(if (isDarkTheme) "☀" else "☾", color = Color.White, fontSize = 19.sp) }
        }
        Spacer(Modifier.width(6.dp))
        Surface(color = Color(0xFF1B344E), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp).clickable(onClick = onSettings)) {
            Box(contentAlignment = Alignment.Center) { Text("⚙", color = Color.White, fontSize = 20.sp) }
        }
    }
    }
}

@Composable
private fun OverviewCard(
    nodes: List<ServerNode>,
    updated: Long?,
    overview: OnlineOverview?,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val onlineNodes = nodes.count { it.health.equals("online", ignoreCase = true) && !it.isDisabledInPanel() }
    val userSampleTime = overview?.sampledAt?.let(::formatMoscowDateTime)
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = BorderStroke(1.dp, CardOutline),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
                Text("Состояние инфраструктуры", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (expanded) "⌃" else "⌄", color = Muted, fontSize = 19.sp)
            }
            Text("${overview?.onlineUsers?.toString() ?: "—"} человек онлайн", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
            Text("$onlineNodes из ${nodes.size} серверов онлайн", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            if (overview?.stale == true) {
                Text("Счётчик пользователей устарел", color = Amber, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
            if (expanded) {
                Text(userSampleTime?.let { "Замер пользователей: $it" } ?: "Замер пользователей: нет данных", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 7.dp))
                Text(updated?.let { "Обновлено ${MoscowDateTimeLabel.format(Instant.ofEpochMilli(it))} МСК · каждые 15 сек" } ?: "Ожидание данных", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                Text("СОСТОЯНИЕ УЗЛОВ", color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 13.dp, bottom = 3.dp))
                nodes.forEachIndexed { index, node ->
                    if (index > 0) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(DividerColor))
                    InfrastructureNodeStatus(node)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("СЕРВЕРЫ", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), color = Muted, fontSize = 10.sp)
        Spacer(Modifier.weight(1f))
        Text("Автообновление 15 с", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun NodeCard(node: ServerNode, onClick: () -> Unit) {
    val latest = node.samples.lastOrNull()
    val disabledInPanel = node.isDisabledInPanel()
    val nodeOnline = node.health.equals("online", ignoreCase = true)
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, CardOutline),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(39.dp).clip(RoundedCornerShape(13.dp)).background(if (node.key.role == "in") LocalMonitorPalette.current.roleInSurface else LocalMonitorPalette.current.roleOutSurface), contentAlignment = Alignment.Center) {
                    Text(node.key.role.uppercase(), color = if (node.key.role == "in") Accent else Color(0xFF9B6CF6), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(node.name, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(node.city, node.country).joinToString(", ").ifBlank { "${node.key.role.uppercase()} · ID ${node.key.id}" }, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                val (connectionText, connectionColor) = if (disabledInPanel) "ВЫКЛЮЧЕН" to Muted else connectionStatus(node.health)
                StatePill(connectionText, connectionColor)
            }
            if (node.metricsStale) StaleNotice(node.sampledAt ?: latest?.sampledAt)
            Row(Modifier.fillMaxWidth().padding(start = 50.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                MiniValue("CPU", latest?.cpuPercent?.let { "${it.toInt()}%" } ?: "—")
                MiniValue("RAM", percent(latest?.ramUsedBytes, latest?.ramTotalBytes)?.let { "${it.toInt()}%" } ?: "—")
                MiniValue("ДИСК", percent(latest?.diskUsedBytes, latest?.diskTotalBytes)?.let { "${it.toInt()}%" } ?: "—")
                MiniValue("XRAY", when {
                    !nodeOnline -> "—"
                    disabledInPanel && node.xrayActive == true -> "Запущен*"
                    node.xrayActive == true -> "Активен"
                    node.xrayActive == false -> "Выкл."
                    else -> "—"
                })
            }
            if (disabledInPanel && nodeOnline && node.xrayActive == true) {
                Text("Узел выключен в панели, но Xray ещё работает", color = Amber, fontSize = 9.sp, modifier = Modifier.padding(start = 50.dp, top = 5.dp))
            }
            Text(
                latest?.sampledAt?.let { formatApiTime(it) }?.let { "Замер $it · нажмите, чтобы открыть графики" } ?: "Нет данных · нажмите для подробностей",
                color = Muted,
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 50.dp, top = 7.dp)
            )
        }
    }
}

@Composable
private fun NodeDetailScreen(node: ServerNode, rangeMinutes: Int, onRangeSelected: (Int) -> Unit, onBack: () -> Unit) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = (screenHeightDp * 0.05f).dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = CardSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp).clickable(onClick = onBack)) {
                Box(contentAlignment = Alignment.Center) { Text("‹", color = Ink, fontSize = 27.sp) }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(node.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${node.key.role.uppercase()} · графики и метрики", color = Muted, fontSize = 10.sp)
            }
            Surface(color = SoftSurface, shape = CircleShape) { Text("МСК", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) }
            val (connectionText, connectionColor) = connectionStatus(node.health)
            StatePill(connectionText, connectionColor)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ПЕРИОД", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            listOf(5, 15, 30).forEach { minutes ->
                val selected = minutes == rangeMinutes
                Surface(color = if (selected) Accent else CardSurface, shape = RoundedCornerShape(9.dp), modifier = Modifier.padding(start = 6.dp).clickable { onRangeSelected(minutes) }) {
                    Text("${minutes}м", color = if (selected) Color.White else Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
        if (node.metricsStale) StaleNotice(node.sampledAt ?: node.samples.lastOrNull()?.sampledAt)
        var activeMetric by remember(node.key) { mutableStateOf("ЦП") }
        if (node.samples.isEmpty()) {
            EmptyState("Нет данных · ждём пакет секундных замеров")
        } else {
            val points = node.samples
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Surface(
                        color = CardSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardOutline),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("ПОКАЗАТЕЛЬ", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Row(
                                Modifier.fillMaxWidth().padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("ЦП", "ОЗУ", "Диск", "Сеть").forEach { metric ->
                                    val selected = activeMetric == metric
                                    Surface(
                                        color = if (selected) Accent else SoftSurface,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).clickable { activeMetric = metric }
                                    ) {
                                        Box(Modifier.fillMaxWidth().padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                metric,
                                                color = if (selected) Color.White else Ink,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            when (activeMetric) {
                                "ЦП" -> MetricChart("ЦП", "%", points, points.map { it.cpuPercent }, rangeMinutes, fixedMaximum = 100.0)
                                "ОЗУ" -> MetricChart("ОЗУ", "%", points, points.map { percent(it.ramUsedBytes, it.ramTotalBytes) }, rangeMinutes, fixedMaximum = 100.0)
                                "Диск" -> MetricChart("Диск", "%", points, points.map { percent(it.diskUsedBytes, it.diskTotalBytes) }, rangeMinutes, fixedMaximum = 100.0)
                                else -> MetricChart("Сеть", "Б/с", points, points.map { it.networkRxBytesPerSec }, rangeMinutes, secondValues = points.map { it.networkTxBytesPerSec }, secondLabel = "OUT")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfrastructureNodeStatus(node: ServerNode) {
    val disabledInPanel = node.isDisabledInPanel()
    val nodeOnline = node.health.equals("online", ignoreCase = true)
    val connection = if (disabledInPanel) "Выключен" to Muted else connectionStatus(node.health)
    val xray = if (!nodeOnline) null else when {
        disabledInPanel && node.xrayActive == true -> "Xray запущен" to Color(0xFFAC6D13)
        node.xrayActive == true -> "Xray работает" to Green
        node.xrayActive == false -> "Xray остановлен" to Red
        else -> "Xray: нет данных" to Muted
    }
    val (sync, syncColor) = syncLabel(node)
    val roleName = if (node.key.role == "in") "Входящий IN" else "Выходящий OUT"
    val place = node.city?.takeIf { it.isNotBlank() } ?: node.name
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("$roleName · $place", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            StatusBadge(connection.first, connection.second)
            xray?.let { StatusBadge(it.first, it.second) }
        }
        val signal = node.lastSeenAt?.let(::formatMoscowDateTime)
            ?: (node.sampledAt ?: node.samples.lastOrNull()?.sampledAt)?.let(::formatMoscowDateTime)
        Text(signal?.let { "Последний сигнал: $it МСК" } ?: "Последний сигнал: нет данных", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
        if (disabledInPanel && nodeOnline && node.xrayActive == true) {
            Text("Узел выключен в панели, но Xray ещё работает", color = Color(0xFFAC6D13), fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
        }
        if (!disabledInPanel) {
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StatusBadge(sync, syncColor)
            }
        }
        val lastReport = reportLabel(node.syncStatus)
        val syncTime = node.syncRanAt?.let(::formatMoscowDateTime)
        val currentSyncState = node.currentSyncState?.takeIf { it.isNotBlank() } ?: legacyCurrentSyncState(node)
        val reportRepeatsCurrentState = node.syncStatus?.equals(currentSyncState, ignoreCase = true) == true
        val reportSummary = if (reportRepeatsCurrentState) {
            "Последний отчёт${syncTime?.let { " · $it МСК" } ?: ""}"
        } else {
            "Последний отчёт: $lastReport${syncTime?.let { " · $it МСК" } ?: ""}"
        }
        Text(
            reportSummary,
            color = Muted,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 5.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (node.syncRanAt == null && node.syncStatus == null) {
            Text("Отчётов ещё нет", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
        }
        node.syncMessage?.let {
            Text("Последний отчёт: $it", color = Ink, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (node.enabledTransports.isNotEmpty()) {
            val isCurrent = node.health.equals("online", ignoreCase = true)
            val protocolLabel = when {
                !isCurrent -> "Последние известные протоколы"
                node.key.role == "in" -> "Входящие протоколы"
                else -> "Протоколы"
            }
            Text("$protocolLabel: ${node.enabledTransports.joinToString(" · ")}", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (node.metricsStale) {
            Text("Метрики устарели", color = Color(0xFFAC6D13), fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.10f), shape = CircleShape) {
        Text(text, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
    }
}

@Composable
private fun StatePill(value: String, color: Color) {
    Surface(color = color.copy(alpha = 0.10f), shape = CircleShape) {
        Text(value, color = color, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = .2.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp))
    }
}

@Composable
private fun syncLabel(node: ServerNode): Pair<String, Color> {
    val state = node.currentSyncState?.takeIf { it.isNotBlank() } ?: legacyCurrentSyncState(node)
    return when (state) {
        "success" -> "Синхронизация ОК" to Green
        "error" -> "Ошибка синхронизации" to Red
        "pending" -> "Ожидается синхронизация" to Color(0xFFAC6D13)
        "dry_run" -> "Пробный режим" to Muted
        "disabled" -> "Узел выключен в панели" to Muted
        "provisioning" -> "Узел настраивается" to Muted
        "unknown" -> "Текущее состояние неизвестно" to Muted
        else -> "Текущее состояние неизвестно" to Muted
    }
}

private fun ServerNode.isDisabledInPanel(): Boolean =
    currentSyncState.equals("disabled", ignoreCase = true)

/** Older FastAPI versions only expose the last report, which is not a live state. */
private fun legacyCurrentSyncState(node: ServerNode): String {
    if (node.health.lowercase() != "online" || node.xrayActive == false) return "unknown"
    return when {
        node.syncPending -> "pending"
        node.syncStatus in setOf("success", "error", "dry_run") -> node.syncStatus!!
        else -> "unknown"
    }
}

private fun reportLabel(status: String?): String = when (status?.lowercase()) {
    "success" -> "Успешно"
    "error" -> "Ошибка"
    "dry_run" -> "Пробный режим"
    null -> "нет отчёта"
    else -> status
}

@Composable
private fun connectionStatus(health: String): Pair<String, Color> = when (health.lowercase()) {
    "online" -> "На связи" to Green
    "offline" -> "Нет связи" to Red
    "unknown", "", "null" -> "Нет данных" to Muted
    else -> "Статус: $health" to Muted
}

@Composable
private fun StaleNotice(sampledAt: String?) {
    val age = formatRelativeAge(sampledAt)
    Surface(color = SoftSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 13.dp)) {
        Text(
            age?.let { "Нет свежих метрик · последние данные $it" } ?: "Нет свежих метрик · ждём новый пакет",
            color = Amber, fontSize = 11.sp, modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
private fun MiniValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(SoftSurface), contentAlignment = Alignment.Center) {
                Text("···", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(message, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun Footer() {
    Text("Метрики из FastAPI · точки хранятся только в памяти", color = Muted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 17.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
private fun AccessAndSettingsDialog(
    config: MonitorConfig,
    updateMessage: String?,
    updateInfo: UpdateInfo?,
    checkingUpdate: Boolean,
    downloadingUpdate: Boolean,
    updateError: String?,
    statisticsClearedMessage: String?,
    onDismiss: () -> Unit,
    onSave: (MonitorConfig) -> Unit,
    onDeleteAccess: () -> Unit,
    onClearStatistics: () -> Unit,
    onCheckUpdate: suspend () -> Unit,
    onDownloadUpdate: suspend () -> Unit
) {
    var base by remember(config.token) { mutableStateOf(config.apiBase) }
    var token by remember(config.token) { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Доступ и обновления", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (config.token.isBlank()) {
                    Text("Monitor Bearer-токен используется только для GET-запроса к API.", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(base, { base = it }, label = { Text("Адрес API") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(token, { token = it }, label = { Text("Monitor Bearer-токен") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                    formError?.let { Text(it, color = Red, fontSize = 11.sp) }
                    Button(onClick = {
                        val cleanBase = base.trim().trimEnd('/')
                        if (!cleanBase.startsWith("https://", true)) formError = "Адрес должен начинаться с https://"
                        else if (token.isBlank()) formError = "Введите Monitor Bearer-токен"
                        else onSave(MonitorConfig(cleanBase, token.trim()))
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(12.dp)) {
                        Text("Сохранить и подключиться")
                    }
                } else {
                    Text("API", color = Muted, fontSize = 10.sp)
                    Text(config.apiBase, color = Ink, fontSize = 13.sp)
                    Text("Bearer-токен сохранён ·••••••••", color = Muted, fontSize = 12.sp)
                    OutlinedButton(onClick = onClearStatistics, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("Удалить данные статистики", color = Red)
                    }
                    statisticsClearedMessage?.let { Text(it, color = Muted, fontSize = 10.sp) }
                    OutlinedButton(onClick = onDeleteAccess, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Удалить доступ", color = Red) }
                }
                Surface(color = SoftSurface, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Версия ${BuildConfig.VERSION_NAME} · сборка ${BuildConfig.VERSION_CODE}", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Button(onClick = { scope.launch { onCheckUpdate() } }, enabled = !checkingUpdate && !downloadingUpdate, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(11.dp)) {
                            Text(if (checkingUpdate) "Проверяем…" else "Проверить обновления")
                        }
                        updateMessage?.let { Text(it, color = if (updateInfo == null) Muted else Green, fontSize = 11.sp) }
                        updateInfo?.let { info ->
                            if (info.notes.isNotBlank()) Text(info.notes, color = Muted, fontSize = 10.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            Button(onClick = { scope.launch { onDownloadUpdate() } }, enabled = !downloadingUpdate, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
                                Text(if (downloadingUpdate) "Скачиваем APK…" else "Скачать и установить")
                            }
                        }
                        updateError?.let { Text(it, color = Red, fontSize = 10.sp) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        containerColor = CardSurface
    )
}

private fun percent(used: Long?, total: Long?): Double? =
    if (used == null || total == null || total <= 0L) null else (used.toDouble() * 100.0 / total).coerceIn(0.0, 100.0)

private fun formatApiTime(value: String?): String? = value?.let {
    runCatching { TimeLabel.format(Instant.parse(it)) }.getOrNull()
}

private fun formatMoscowDateTime(value: String?): String? = value?.let {
    runCatching { MoscowDateTimeLabel.format(Instant.parse(it)) }.getOrNull()
}

private fun formatRelativeAge(value: String?): String? = value?.let {
    runCatching {
        val seconds = (Instant.now().epochSecond - Instant.parse(it).epochSecond).coerceAtLeast(0L)
        when {
            seconds < 60 -> "$seconds сек назад"
            seconds < 3600 -> "${seconds / 60} мин назад"
            else -> "${seconds / 3600} ч назад"
        }
    }.getOrNull()
}

