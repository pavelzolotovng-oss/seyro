package ru.seyro.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.seyro.monitor.ui.MonitorApp
import ru.seyro.monitor.ui.theme.SeyroMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SeyroMonitorTheme { MonitorApp() } }
    }
}
