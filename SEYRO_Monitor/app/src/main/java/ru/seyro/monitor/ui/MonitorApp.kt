package ru.seyro.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.seyro.monitor.data.NodeHealth
import ru.seyro.monitor.data.NodeRole

private enum class Screen { OVERVIEW, SETTINGS }

@Composable
fun MonitorApp() {
    var screen by remember { mutableStateOf(Screen.OVERVIEW) }
    Scaffold(
        topBar = { AppBar() },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = screen == Screen.OVERVIEW, onClick = { screen = Screen.OVERVIEW }, icon = { Text("◉") }, label = { Text("Обзор") })
                NavigationBarItem(selected = screen == Screen.SETTINGS, onClick = { screen = Screen.SETTINGS }, icon = { Text("⚙") }, label = { Text("Доступ") })
            }
        }
    ) { padding ->
        when (screen) {
            Screen.OVERVIEW -> Overview(Modifier.padding(padding), onOpenSettings = { screen = Screen.SETTINGS })
            Screen.SETTINGS -> Settings(Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar() = TopAppBar(title = {
    Column { Text("SEYRO Monitor"); Text("Read-only infrastructure status", style = MaterialTheme.typography.labelSmall) }
})

@Composable
private fun Overview(modifier: Modifier, onOpenSettings: () -> Unit) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Подключение не настроено", style = MaterialTheme.typography.titleMedium)
                    Text("Приложение использует только read-only API FastAPI. Управление узлами, SSH и VPN-функции намеренно отсутствуют.")
                    Button(onClick = onOpenSettings) { Text("Настроить доступ") }
                }
            }
        }
        item { Text("Будущие статусы узлов", style = MaterialTheme.typography.titleMedium) }
        item { NodePreview("IN", "Входящий сервер", NodeRole.IN, NodeHealth.UNKNOWN) }
        item { NodePreview("OUT", "Исходящие серверы", NodeRole.OUT, NodeHealth.UNKNOWN) }
    }
}

@Composable
private fun NodePreview(kind: String, title: String, role: NodeRole, health: NodeHealth) {
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(kind, modifier = Modifier.size(44.dp).padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text("${role.name} · ${health.name}", style = MaterialTheme.typography.bodySmall)
            }
            Text("—", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun Settings(modifier: Modifier) {
    var endpoint by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Read-only API", style = MaterialTheme.typography.titleLarge)
        Text("Укажите URL FastAPI и отдельный monitor-token. Админская cookie-сессия и технические Xray-токены не поддерживаются.")
        OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Base URL") }, placeholder = { Text("https://monitor.example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Monitor token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { }, enabled = endpoint.isNotBlank() && token.isNotBlank()) { Text("Сохранить после подключения API") }
        HorizontalDivider()
        Text("Контракт: GET /api/v1/monitor/overview, /nodes и /nodes/{role}/{id}/metrics. До появления этих маршрутов приложение не отправляет запросы и не показывает тестовые показатели как реальные.", style = MaterialTheme.typography.bodySmall)
    }
}
