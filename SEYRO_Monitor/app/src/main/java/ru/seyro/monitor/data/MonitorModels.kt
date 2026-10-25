package ru.seyro.monitor.data

data class ServerKey(val role: String, val id: Int)

data class MetricSample(
    val sampledAt: String,
    val epochMillis: Long,
    val cpuPercent: Double?,
    val ramUsedBytes: Long?,
    val ramTotalBytes: Long?,
    val diskUsedBytes: Long?,
    val diskTotalBytes: Long?,
    val networkRxBytesPerSec: Double?,
    val networkTxBytesPerSec: Double?,
    val uptimeSeconds: Long?
)

data class ServerNode(
    val key: ServerKey,
    val name: String,
    val city: String?,
    val country: String?,
    val health: String,
    val xrayActive: Boolean,
    val metricsStale: Boolean,
    val sampledAt: String?,
    val samples: List<MetricSample>
)

data class MonitorConfig(val apiBase: String, val token: String)
