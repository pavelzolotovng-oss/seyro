package ru.seyro.monitor.data

import android.content.Context

class MonitorConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("monitor_access", Context.MODE_PRIVATE)

    fun read(): MonitorConfig = MonitorConfig(
        apiBase = prefs.getString("api_base", "https://admin.seyro.ru") ?: "https://admin.seyro.ru",
        token = prefs.getString("token", "") ?: ""
    )

    fun save(config: MonitorConfig) {
        prefs.edit().putString("api_base", config.apiBase.trim().trimEnd('/'))
            .putString("token", config.token.trim()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
