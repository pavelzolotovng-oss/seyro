package ru.seyro.monitor.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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

private val Page = Color(0xFFF4F7FC)
private val Ink = Color(0xFF17233A)
private val Muted = Color(0xFF8290A5)
private val Accent = Color(0xFF356AF4)
private val Green = Color(0xFF17A77A)
private val Red = Color(0xFFD65B64)
private val TimeLabel = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun MonitorScreen() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val configStore = remember { MonitorConfigStore(context) }
    val pollGate = remember { AtomicBoolean(false) }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    var config by remember { mutableStateOf(configStore.read()) }
    var nodes by remember { mutableStateOf<List<ServerNode>>(emptyList()) }
    var overview by remember { mutableStateOf<OnlineOverview?>(null) }
    var lastUpdated by remember { mutableStateOf<Long?>(null) }
    var historyStartAt by remember { mutableStateOf(0L) }
    var statisticsClearedMessage by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val expanded = remember { mutableStateMapOf<ServerKey, Boolean>() }
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

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Page) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = (screenHeightDp * 0.05f).dp)) {
                Header(onSettings = { showSettings = true })
                if (config.token.isNotBlank()) {
                    OverviewCard(nodes, lastUpdated, overview)
                    if (loadError != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F1)),
                            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Text("Нет связи: $loadError · повторим автоматически", color = Red, fontSize = 12.sp, modifier = Modifier.padding(13.dp))
                        }
                    }
                    if (isLoading && nodes.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                                Text("Загружаем узлы…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    } else if (nodes.isEmpty() && loadError == null) {
                        EmptyState("API пока не вернул узлы")
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item { SectionHeader(nodes.size) }
                            items(nodes, key = { "${it.key.role}:${it.key.id}" }) { node ->
                                NodeCard(node, expanded[node.key] == true) { expanded[node.key] = expanded[node.key] != true }
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

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Accent), contentAlignment = Alignment.Center) {
            Text("S", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text("SEYRO", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Text("MONITORING", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.1.sp)
        }
        Surface(color = Color(0xFFE8F7F1), shape = CircleShape) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Green))
                Text(" LIVE", color = Color(0xFF188966), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(9.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(13.dp), modifier = Modifier.size(40.dp).clickable(onClick = onSettings)) {
            Box(contentAlignment = Alignment.Center) { Text("⚙", color = Ink, fontSize = 20.sp) }
        }
    }
}

@Composable
private fun OverviewCard(nodes: List<ServerNode>, updated: Long?, overview: OnlineOverview?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF172944)), shape = RoundedCornerShape(21.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Состояние инфраструктуры", color = Color(0xFFB9C6D9), fontSize = 12.sp)
                Text("${nodes.count { it.health == "online" }} из ${nodes.size} серверов онлайн", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                Text("${overview?.onlineUsers?.toString() ?: "—"} человек онлайн", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 9.dp))
                if (overview?.stale == true) {
                    Text("Счётчик пользователей устарел", color = Color(0xFFFFD18A), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                }
                val userSampleTime = overview?.sampledAt?.let(::formatApiTime)
                Text(userSampleTime?.let { "Замер пользователей: $it" } ?: "Замер пользователей: нет данных", color = Color(0xFF8EA2BF), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                Text(updated?.let { "Обновлено ${TimeLabel.format(Instant.ofEpochMilli(it))} · каждые 15 сек" } ?: "Ожидание данных", color = Color(0xFF8EA2BF), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF25436A)), contentAlignment = Alignment.Center) {
                Text("${nodes.count { it.health == "online" }}/${nodes.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SectionHeader(count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 21.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("УЗЛЫ", color = Muted, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), color = Muted, fontSize = 10.sp)
        Spacer(Modifier.weight(1f))
        Text("Автообновление 15 с", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun NodeCard(node: ServerNode, isExpanded: Boolean, onToggle: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 15.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(39.dp).clip(RoundedCornerShape(13.dp)).background(if (node.key.role == "in") Color(0xFFEAF0FF) else Color(0xFFF0EBFF)), contentAlignment = Alignment.Center) {
                    Text(if (node.key.role == "in") "IN" else "OUT", color = if (node.key.role == "in") Accent else Color(0xFF8056DC), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(node.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(node.city, node.country).joinToString(", ").ifBlank { "${node.key.role.uppercase()} · ID ${node.key.id}" }, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    val nodeSampleTime = formatApiTime(node.sampledAt ?: node.samples.lastOrNull()?.sampledAt)
                    Text(nodeSampleTime?.let { "Данные получены: $it" } ?: "Данные ещё не получены", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
                }
                StatusPill(node)
                Text(if (isExpanded) "⌃" else "⌄", color = Muted, fontSize = 19.sp, modifier = Modifier.padding(start = 9.dp))
            }
            if (!isExpanded) {
                Row(Modifier.fillMaxWidth().padding(start = 50.dp, top = 11.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    MiniValue("CPU", node.samples.lastOrNull()?.cpuPercent?.let { "${it.toInt()}%" } ?: "—")
                    MiniValue("RAM", percent(node.samples.lastOrNull()?.ramUsedBytes, node.samples.lastOrNull()?.ramTotalBytes)?.let { "${it.toInt()}%" } ?: "—")
                    MiniValue("ДИСК", percent(node.samples.lastOrNull()?.diskUsedBytes, node.samples.lastOrNull()?.diskTotalBytes)?.let { "${it.toInt()}%" } ?: "—")
                }
            }
            if (isExpanded) {
                if (node.metricsStale) StaleNotice()
                if (node.samples.isEmpty()) {
                    Text("Нет данных · ждём пакет секундных замеров", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp, bottom = 5.dp))
                } else {
                    val p = node.samples
                    MetricChart("CPU", "%", p, p.map { it.cpuPercent }, fixedMaximum = 100.0)
                    MetricChart("RAM", "%", p, p.map { percent(it.ramUsedBytes, it.ramTotalBytes) }, fixedMaximum = 100.0)
                    MetricChart("Диск", "%", p, p.map { percent(it.diskUsedBytes, it.diskTotalBytes) }, fixedMaximum = 100.0)
                    MetricChart("Сеть", "Б/с", p, p.map { it.networkRxBytesPerSec }, secondValues = p.map { it.networkTxBytesPerSec }, secondLabel = "OUT")
                    Text("${p.size} точек за последние 30 минут", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusPill(node: ServerNode) {
    val text = when {
        node.metricsStale -> "УСТАРЕЛИ"
        node.health == "online" -> "ОНЛАЙН"
        else -> "ОФЛАЙН"
    }
    val color = when {
        node.metricsStale -> Color(0xFFAC6D13)
        node.health == "online" -> Green
        else -> Red
    }
    Surface(color = color.copy(alpha = 0.10f), shape = CircleShape) {
        Text(text, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
    }
}

@Composable
private fun StaleNotice() {
    Surface(color = Color(0xFFFFF4E5), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 13.dp)) {
        Text("Данные узла устарели · ждём новый пакет", color = Color(0xFF9A6418), fontSize = 11.sp, modifier = Modifier.padding(10.dp))
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
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFFE9EFF9)), contentAlignment = Alignment.Center) {
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
                Surface(color = Color(0xFFF3F6FB), shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
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
        containerColor = Color.White
    )
}

private fun percent(used: Long?, total: Long?): Double? =
    if (used == null || total == null || total <= 0L) null else (used.toDouble() * 100.0 / total).coerceIn(0.0, 100.0)

private fun formatApiTime(value: String?): String? = value?.let {
    runCatching { TimeLabel.format(Instant.parse(it)) }.getOrNull()
}
