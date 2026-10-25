package ru.seyro.monitor.data

data class MonitorOverview(
    val generatedAt: String,
    val nodesTotal: Int,
    val nodesOnline: Int,
    val nodesOffline: Int,
    val nodesXrayStopped: Int,
    val nodesUnknown: Int,
    val activeInNodes: Int,
    val activeOutNodes: Int,
    val syncPendingNodes: Int,
    val onlineUsers: Int?,
    val onlineUsersSampledAt: String?,
    val onlineUsersStale: Boolean
)

data class MonitorNode(
    val id: Long,
    val role: NodeRole,
    val name: String,
    val country: String?,
    val city: String?,
    val configuredStatus: String,
    val health: NodeHealth,
    val lastSeenAt: String?,
    val xrayActive: Boolean?,
    val enabledTransports: List<String>,
    val syncStatus: String?,
    val syncRanAt: String?,
    val syncMessage: String?,
    val syncPending: Boolean,
    val metricsAvailable: Boolean,
    val metricsStale: Boolean,
    val metrics: NodeMetrics?
)

data class NodeMetrics(
    val cpuPercent: Double?,
    val ramUsedBytes: Long,
    val ramTotalBytes: Long,
    val diskUsedBytes: Long,
    val diskTotalBytes: Long,
    val networkRxBytesPerSec: Double?,
    val networkTxBytesPerSec: Double?,
    val uptimeSeconds: Long,
    val sampledAt: String,
    val samples: List<NodeMetricSample> = emptyList()
)

data class NodeMetricSample(
    val cpuPercent: Double?,
    val ramUsedBytes: Long?,
    val ramTotalBytes: Long?,
    val diskUsedBytes: Long?,
    val diskTotalBytes: Long?,
    val networkRxBytesPerSec: Double?,
    val networkTxBytesPerSec: Double?,
    val sampledAt: String
)

data class MonitorSystemStatus(
    val apiStatus: String,
    val databaseStatus: String,
    val generatedAt: String,
    val monitorApiVersion: Int
)

data class MonitorSnapshot(
    val overview: MonitorOverview,
    val nodes: List<MonitorNode>,
    val system: MonitorSystemStatus
)

data class MonitorConfig(val baseUrl: String, val token: String)

enum class NodeRole { IN, OUT }
enum class NodeHealth { ONLINE, OFFLINE, XRAY_STOPPED, UNKNOWN, DISABLED }
