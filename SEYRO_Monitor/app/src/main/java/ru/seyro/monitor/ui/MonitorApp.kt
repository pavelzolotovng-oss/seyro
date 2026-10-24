package ru.seyro.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.seyro.monitor.BuildConfig
import ru.seyro.monitor.data.MonitorApi
import ru.seyro.monitor.data.MonitorConfig
import ru.seyro.monitor.data.MonitorConfigStore
import ru.seyro.monitor.data.MonitorNode
import ru.seyro.monitor.data.MonitorSnapshot
import ru.seyro.monitor.data.NodeHealth
import ru.seyro.monitor.data.NodeRole
import ru.seyro.monitor.update.UpdateCheck
import ru.seyro.monitor.update.UpdateDownloader
import ru.seyro.monitor.update.UpdateInfo
import ru.seyro.monitor.update.UpdateRepository

private enum class Screen { OVERVIEW, SETTINGS }

@Composable
fun MonitorApp() {
    val context = LocalContext.current
    val configStore = remember(context) { MonitorConfigStore(context) }
    val config by configStore.config.collectAsState(initial = MonitorConfig("https://admin.seyro.ru", ""))
    val api = remember { MonitorApi() }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.OVERVIEW) }
    var snapshot by remember { mutableStateOf<MonitorSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    fun refresh(using: MonitorConfig = config) {
        scope.launch {
            loading = true
            connectionError = null
            try {
                snapshot = api.load(using)
            } catch (error: Exception) {
                connectionError = error.message ?: "Не удалось получить данные сервера"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(config.baseUrl, config.token) {
        if (config.token.isNotBlank()) refresh()
    }

    Scaffold(
        topBar = { AppBar() },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == Screen.OVERVIEW,
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
                onRefresh = { refresh() },
                onOpenSettings = { screen = Screen.SETTINGS }
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
    Column {
        Text("SEYRO Monitor")
        Text("Статусы серверов · только чтение", style = MaterialTheme.typography.labelSmall)
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
    onOpenSettings: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (snapshot == null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (configured) "Нет данных от сервера" else "Подключите SEYRO API", style = MaterialTheme.typography.titleMedium)
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
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Состояние инфраструктуры", style = MaterialTheme.typography.titleMedium)
                    Text("Узлов: ${overview.nodesTotal} · онлайн: ${overview.nodesOnline}")
                    Text("Офлайн: ${overview.nodesOffline} · Xray остановлен: ${overview.nodesXrayStopped}")
                    Text("Неизвестно: ${overview.nodesUnknown} · ожидают синхронизации: ${overview.syncPendingNodes}")
                    Text("IN активно: ${overview.activeInNodes} · OUT активно: ${overview.activeOutNodes}")
                    Text("Снимок: ${prettyTime(overview.generatedAt)}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "API ${snapshot.system.apiStatus} · база ${snapshot.system.databaseStatus}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = onRefresh, enabled = !loading) {
                        Text(if (loading) "Обновляем…" else "Обновить")
                    }
                    if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (snapshot.nodes.none { it.metricsAvailable }) {
            item {
                Text(
                    "Сервер пока не передаёт CPU, RAM, диск и сетевые метрики. Доступны статусы узлов и Xray.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item { Text("Серверы", style = MaterialTheme.typography.titleLarge) }
        if (snapshot.nodes.isEmpty()) {
            item { Text("В API пока нет узлов.") }
        } else {
            items(snapshot.nodes, key = { "${it.role}-${it.id}" }) { node -> NodeCard(node) }
        }
    }
}

@Composable
private fun NodeCard(node: MonitorNode) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(node.name, style = MaterialTheme.typography.titleMedium)
                Text(healthLabel(node.health), style = MaterialTheme.typography.labelLarge)
            }
            val location = listOfNotNull(node.city, node.country).joinToString(", ")
            Text("${if (node.role == NodeRole.IN) "IN · Входящий" else "OUT · Исходящий"} · ID ${node.id}")
            if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodySmall)
            Text("Конфигурация: ${node.configuredStatus}")
            Text("Xray: ${node.xrayActive?.let { if (it) "работает" else "остановлен" } ?: "нет данных"}")
            Text("Транспорты: ${node.enabledTransports.takeIf { it.isNotEmpty() }?.joinToString() ?: "—"}")
            Text("Последний сигнал: ${prettyTime(node.lastSeenAt)}", style = MaterialTheme.typography.bodySmall)
            if (node.syncPending) Text("Ожидает синхронизации")
            node.syncStatus?.let { Text("Синхронизация: $it · ${prettyTime(node.syncRanAt)}", style = MaterialTheme.typography.bodySmall) }
            node.syncMessage?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Подключение к серверу", style = MaterialTheme.typography.titleLarge) }
        item {
            Text("Введите отдельный monitor-token с правами только на чтение. Токен будет сохранён в зашифрованном виде на этом устройстве.")
        }
        item {
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text("Base URL") },
                placeholder = { Text("https://admin.seyro.ru") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(if (config.token.isBlank()) "Monitor token" else "Новый токен (необязательно)") },
                placeholder = { Text(if (config.token.isBlank()) "Вставьте выданный токен" else "Оставьте пустым для текущего токена") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (config.token.isNotBlank()) item { Text("Токен уже сохранён на этом устройстве.", style = MaterialTheme.typography.bodySmall) }
        item {
            Button(
                onClick = {
                    val enteredToken = token.trim().ifBlank { config.token }
                    scope.launch {
                        busy = true
                        status = "Сохраняем настройки и проверяем API…"
                        try {
                            val newConfig = MonitorConfig(endpoint, enteredToken)
                            configStore.save(newConfig.baseUrl, newConfig.token)
                            val result = api.load(newConfig)
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
                enabled = !busy && endpoint.isNotBlank() && (token.isNotBlank() || config.token.isNotBlank())
            ) { Text(if (busy) "Проверяем…" else "Сохранить и подключиться") }
        }
        if (status.isNotBlank()) item { Text(status, style = MaterialTheme.typography.bodySmall) }
        if (config.token.isNotBlank()) {
            item {
                Button(onClick = {
                    scope.launch {
                        configStore.clear()
                        token = ""
                        status = "Токен удалён с устройства."
                    }
                }, enabled = !busy) { Text("Удалить сохранённый токен") }
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
                        UpdateCheck.NoUpdate -> {
                            availableUpdate = null
                            updateState = "Установлена актуальная версия"
                        }
                        is UpdateCheck.Available -> {
                            availableUpdate = result.update
                            updateState = "Доступна версия ${result.update.versionName}"
                        }
                        is UpdateCheck.Failed -> {
                            availableUpdate = null
                            updateState = result.message
                        }
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
