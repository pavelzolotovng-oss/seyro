package ru.seyro.monitor.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
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
    suspend fun check(currentVersionCode: Int): UpdateCheck = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext UpdateCheck.Failed("Манифест обновления недоступен")
                val root = JSONObject(connection.inputStream.bufferedReader().use { reader -> reader.readText() })
                if (root.isNull("latest")) return@withContext UpdateCheck.NoUpdate
                val latest = root.getJSONObject("latest")
                val info = UpdateInfo(
                    versionCode = latest.getInt("version_code"),
                    versionName = latest.getString("version_name"),
                    apkUrl = latest.getString("apk_url"),
                    sha256 = latest.getString("sha256").lowercase(),
                    releaseNotes = latest.optString("release_notes")
                )
                when {
                    info.versionCode <= currentVersionCode -> UpdateCheck.NoUpdate
                    !info.apkUrl.startsWith("https://") -> UpdateCheck.Failed("Небезопасный URL обновления")
                    !SHA_256.matches(info.sha256) -> UpdateCheck.Failed("Некорректная контрольная сумма")
                    else -> UpdateCheck.Available(info)
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            UpdateCheck.Failed("Не удалось проверить обновления")
        }
    }

    private companion object {
        const val MANIFEST_URL = "https://raw.githubusercontent.com/pavelzolotovng-oss/seyro/main/SEYRO_Monitor/update.json"
        val SHA_256 = Regex("[0-9a-f]{64}")
    }
}
