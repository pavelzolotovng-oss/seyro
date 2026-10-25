package ru.seyro.monitor.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

object MonitorApi {
    fun fetchOverview(config: MonitorConfig): OnlineOverview {
        val base = config.apiBase.trim().trimEnd('/')
        require(base.startsWith("https://", ignoreCase = true)) { "Адрес API должен начинаться с https://" }
        val connection = (URL("$base/api/v1/monitor/overview").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.token}")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("API обзора ответил HTTP $status")
            val body = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val usersValue = body.opt("online_users")
            val users = (usersValue as? Number)?.toInt()
            val sampledAt = sequenceOf("sampled_at", "online_users_sampled_at", "generated_at")
                .map { body.optString(it) }
                .firstOrNull { it.isNotBlank() && it != "null" }
            return OnlineOverview(users, sampledAt, body.optBoolean("online_users_stale", false))
        } finally {
            connection.disconnect()
        }
    }

    fun fetchNodes(config: MonitorConfig): List<ServerNode> {
        val base = config.apiBase.trim().trimEnd('/')
        require(base.startsWith("https://", ignoreCase = true)) { "Адрес API должен начинаться с https://" }
        val connection = (URL("$base/api/v1/monitor/nodes").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.token}")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("API ответил HTTP $status")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
            return buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val role = item.optString("role").lowercase()
                    val id = item.optInt("id", -1)
                    if (role !in setOf("in", "out") || id < 0) continue
                    val metrics = item.optJSONObject("metrics") ?: JSONObject()
                    add(
                        ServerNode(
                            key = ServerKey(role, id),
                            name = item.optString("name", "${role.uppercase()} #$id"),
                            city = item.optString("city").takeUnless { it == "null" },
                            country = item.optString("country").takeUnless { it == "null" },
                            health = item.optString("health", "unknown"),
                            lastSeenAt = item.nullableString("last_seen_at"),
                            xrayActive = item.opt("xray_active") as? Boolean,
                            syncStatus = item.nullableString("sync_status"),
                            syncRanAt = item.nullableString("sync_ran_at"),
                            syncMessage = item.nullableString("sync_message"),
                            syncPending = item.optBoolean("sync_pending", false),
                            enabledTransports = item.optJSONArray("enabled_transports")?.let { transports ->
                                (0 until transports.length()).mapNotNull { transportIndex ->
                                    transports.optString(transportIndex).takeIf { it.isNotBlank() && it != "null" }
                                }
                            }.orEmpty(),
                            metricsStale = item.optBoolean("metrics_stale", false),
                            sampledAt = metrics.optString("sampled_at").takeUnless { it.isBlank() || it == "null" },
                            samples = parseSamples(metrics.optJSONArray("samples"))
                        )
                    )
                }
            }.sortedWith(compareBy<ServerNode> { it.key.role }.thenBy { it.key.id })
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSamples(array: JSONArray?): List<MetricSample> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val sampledAt = obj.optString("sampled_at").takeUnless { it.isBlank() || it == "null" } ?: continue
            val epoch = runCatching { Instant.parse(sampledAt).toEpochMilli() }.getOrNull() ?: continue
            add(
                MetricSample(
                    sampledAt = sampledAt,
                    epochMillis = epoch,
                    cpuPercent = obj.number("cpu_percent"),
                    ramUsedBytes = obj.longNumber("ram_used_bytes"),
                    ramTotalBytes = obj.longNumber("ram_total_bytes"),
                    diskUsedBytes = obj.longNumber("disk_used_bytes"),
                    diskTotalBytes = obj.longNumber("disk_total_bytes"),
                    networkRxBytesPerSec = obj.number("network_rx_bytes_per_sec"),
                    networkTxBytesPerSec = obj.number("network_tx_bytes_per_sec"),
                    uptimeSeconds = obj.longNumber("uptime_seconds")
                )
            )
        }
    }

    private fun JSONObject.number(name: String): Double? {
        val value = opt(name)
        return (value as? Number)?.toDouble()?.takeIf { it.isFinite() }
    }

    private fun JSONObject.nullableString(name: String): String? = optString(name)
        .takeUnless { it.isBlank() || it == "null" }

    private fun JSONObject.longNumber(name: String): Long? {
        val value = opt(name)
        return (value as? Number)?.toLong()
    }
}

