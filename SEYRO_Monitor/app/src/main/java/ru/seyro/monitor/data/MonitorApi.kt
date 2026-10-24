package ru.seyro.monitor.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MonitorApi {
    suspend fun load(config: MonitorConfig): MonitorSnapshot = withContext(Dispatchers.IO) {
        require(config.token.isNotBlank()) { "Сначала введите monitor-token в разделе «Доступ»" }
        val baseUrl = config.baseUrl.trim().trimEnd('/')
        val overview = parseOverview(getJson(baseUrl, config.token, "/api/v1/monitor/overview"))
        val nodesResponse = getJson(baseUrl, config.token, "/api/v1/monitor/nodes")
        val nodesJson = nodesResponse.getJSONArray("items")
        val nodes = List(nodesJson.length()) { index -> parseNode(nodesJson.getJSONObject(index)) }
        val system = parseSystem(getJson(baseUrl, config.token, "/api/v1/monitor/system/status"))
        MonitorSnapshot(overview, nodes, system)
    }

    private fun getJson(baseUrl: String, token: String, path: String): JSONObject {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw HttpApiException(httpError(status, body))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } catch (error: HttpApiException) {
            throw error
        } catch (error: IOException) {
            throw IOException("Не удалось связаться с сервером. Проверьте интернет и HTTPS адрес.", error)
        } catch (error: Exception) {
            throw IOException("Сервер вернул неожиданный ответ. Проверьте доступность Monitor API.", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun httpError(status: Int, body: String): String = when (status) {
        401, 403 -> "Сервер отклонил monitor-token (HTTP $status). Проверьте токен и его read-only доступ."
        404 -> "Сервер не нашёл Monitor API (HTTP 404). Проверьте адрес сервера и маршруты."
        in 500..599 -> "Ошибка на сервере (HTTP $status). Попробуйте позже."
        else -> {
            val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull()
            if (!detail.isNullOrBlank()) "Ошибка API (HTTP $status): $detail"
            else "Сервер вернул HTTP $status"
        }
    }

    private fun parseOverview(json: JSONObject) = MonitorOverview(
        generatedAt = json.getString("generated_at"),
        nodesTotal = json.getInt("nodes_total"),
        nodesOnline = json.getInt("nodes_online"),
        nodesOffline = json.getInt("nodes_offline"),
        nodesXrayStopped = json.getInt("nodes_xray_stopped"),
        nodesUnknown = json.getInt("nodes_unknown"),
        activeInNodes = json.getInt("active_in_nodes"),
        activeOutNodes = json.getInt("active_out_nodes"),
        syncPendingNodes = json.getInt("sync_pending_nodes")
    )

    private fun parseNode(json: JSONObject): MonitorNode = MonitorNode(
        id = json.getLong("id"),
        role = when (json.getString("role").lowercase(Locale.ROOT)) {
            "in" -> NodeRole.IN
            "out" -> NodeRole.OUT
            else -> throw IOException("Сервер вернул неизвестную роль узла")
        },
        name = json.getString("name"),
        country = json.nullableString("country"),
        city = json.nullableString("city"),
        configuredStatus = json.getString("configured_status"),
        health = when (json.getString("health").lowercase(Locale.ROOT)) {
            "online" -> NodeHealth.ONLINE
            "offline" -> NodeHealth.OFFLINE
            "xray_stopped" -> NodeHealth.XRAY_STOPPED
            "disabled" -> NodeHealth.DISABLED
            "unknown" -> NodeHealth.UNKNOWN
            else -> NodeHealth.UNKNOWN
        },
        lastSeenAt = json.nullableString("last_seen_at"),
        xrayActive = json.nullableBoolean("xray_active"),
        enabledTransports = json.getJSONArray("enabled_transports").toStringList(),
        syncStatus = json.nullableString("sync_status"),
        syncRanAt = json.nullableString("sync_ran_at"),
        syncMessage = json.nullableString("sync_message"),
        syncPending = json.getBoolean("sync_pending"),
        metricsAvailable = json.getBoolean("metrics_available")
    )

    private fun parseSystem(json: JSONObject) = MonitorSystemStatus(
        apiStatus = json.getString("api_status"),
        databaseStatus = json.getString("database_status"),
        generatedAt = json.getString("generated_at"),
        monitorApiVersion = json.getInt("monitor_api_version")
    )

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONObject.nullableBoolean(key: String): Boolean? = if (isNull(key)) null else getBoolean(key)

    private fun JSONArray.toStringList(): List<String> = List(length()) { index -> getString(index) }

    private class HttpApiException(message: String) : IOException(message)

    private companion object {
        const val TIMEOUT_MS = 12_000
    }
}
