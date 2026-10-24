package ru.seyro.monitor.data

/** Stable models for the future read-only /api/v1/monitor contract. */
data class MonitorOverview(val generatedAt: String, val activeNodes: Int, val offlineNodes: Int, val xrayStoppedNodes: Int)

data class MonitorNode(
    val id: Long, val role: NodeRole, val name: String, val country: String?, val city: String?,
    val configuredStatus: String, val health: NodeHealth, val lastSeenAt: String?, val xray: XrayState
)

enum class NodeRole { IN, OUT }
enum class NodeHealth { ONLINE, OFFLINE, XRAY_STOPPED, UNKNOWN, DISABLED }
data class XrayState(val active: Boolean?, val enabledTransports: List<String>, val syncStatus: String?, val syncPending: Boolean)
