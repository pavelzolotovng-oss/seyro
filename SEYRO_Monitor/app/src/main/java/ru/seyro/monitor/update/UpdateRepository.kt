package ru.seyro.monitor.update

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String
)

sealed interface UpdateCheck {
    data object NoUpdate : UpdateCheck
    data class Available(val update: UpdateInfo) : UpdateCheck
    data class Failed(val message: String) : UpdateCheck
}

class UpdateRepository {
    suspend fun check(currentVersionName: String): UpdateCheck = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "SEYRO-Monitor/$currentVersionName")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateCheck.Failed("Не удалось получить список релизов GitHub")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (body.length > MAX_RESPONSE_CHARS) return@withContext UpdateCheck.Failed("Список релизов слишком большой")
            val releases = JSONArray(body)
            var newest: UpdateInfo? = null
            for (index in 0 until releases.length()) {
                val release = releases.optJSONObject(index) ?: continue
                if (release.optBoolean("draft", true)) continue
                val tag = release.optString("tag_name")
                val version = tag.removePrefix(TAG_PREFIX)
                if (version == tag || compareVersions(version, currentVersionName) <= 0) continue

                val assets = release.optJSONArray("assets") ?: continue
                var apkUrl: String? = null
                var sha256 = ""
                for (assetIndex in 0 until assets.length()) {
                    val asset = assets.optJSONObject(assetIndex) ?: continue
                    if (asset.optString("name") != APK_ASSET) continue
                    apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://github.com/") }
                    sha256 = asset.optString("digest").removePrefix("sha256:").lowercase()
                    break
                }
                if (apkUrl == null || !SHA_256.matches(sha256)) continue

                val candidate = UpdateInfo(
                    versionName = version,
                    apkUrl = apkUrl,
                    sha256 = sha256,
                    releaseNotes = release.optString("body").ifBlank { "Для этой версии нет описания изменений." }
                )
                if (newest == null || compareVersions(candidate.versionName, newest.versionName) > 0) newest = candidate
            }
            newest?.let { UpdateCheck.Available(it) } ?: UpdateCheck.NoUpdate
        } catch (_: Exception) {
            UpdateCheck.Failed("Не удалось проверить релизы GitHub")
        } finally {
            connection?.disconnect()
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = VERSION.matchEntire(left)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: 0 } ?: return -1
        val rightParts = VERSION.matchEntire(right)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: 0 } ?: return -1
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val comparison = (leftParts.getOrElse(index) { 0 }).compareTo(rightParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/pavelzolotovng-oss/seyro/releases?per_page=100"
        const val TAG_PREFIX = "seyro-monitor-v"
        const val APK_ASSET = "seyro-monitor.apk"
        const val MAX_RESPONSE_CHARS = 1_000_000
        val VERSION = Regex("(\\d+)\\.(\\d+)\\.(\\d+)")
        val SHA_256 = Regex("[0-9a-f]{64}")
    }
}
