package ru.seyro.monitor.update

import android.content.Context
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    val releaseUrl: String
)

object UpdateRepository {
    private const val RELEASES_API = "https://api.github.com/repos/pavelzolotovng-oss/seyro/releases?per_page=100"
    private val tagPattern = Regex("^seyro-monitor-v0\\.1-build-(\\d+)$")

    fun check(currentVersionCode: Int): UpdateInfo? {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "SEYRO-Monitor-Android")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("GitHub ответил HTTP ${connection.responseCode}")
            val releases = JSONArray(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            var newest: UpdateInfo? = null
            for (index in 0 until releases.length()) {
                val release = releases.optJSONObject(index) ?: continue
                val tag = release.optString("tag_name")
                val build = tagPattern.matchEntire(tag)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: continue
                val code = 1000 + build
                if (code <= currentVersionCode || code <= (newest?.versionCode ?: 0)) continue
                val assets = release.optJSONArray("assets") ?: continue
                var apkUrl: String? = null
                for (assetIndex in 0 until assets.length()) {
                    val asset = assets.optJSONObject(assetIndex) ?: continue
                    if (asset.optString("name") == "seyro-monitor.apk") {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                        break
                    }
                }
                if (apkUrl != null) {
                    newest = UpdateInfo(
                        versionName = release.optString("name", "SEYRO Monitor 0.1"),
                        versionCode = code,
                        notes = release.optString("body").take(1200),
                        apkUrl = apkUrl,
                        releaseUrl = release.optString("html_url")
                    )
                }
            }
            return newest
        } finally {
            connection.disconnect()
        }
    }

    fun download(context: Context, info: UpdateInfo): File {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(directory, "seyro-monitor.apk.part")
        val apk = File(directory, "seyro-monitor.apk")
        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "SEYRO-Monitor-Android")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("Не удалось скачать APK: HTTP ${connection.responseCode}")
            connection.inputStream.use { input -> partial.outputStream().use { output -> input.copyTo(output) } }
            check(partial.length() > 0L) { "GitHub вернул пустой APK" }
            if (apk.exists()) apk.delete()
            check(partial.renameTo(apk)) { "Не удалось сохранить APK" }
            return apk
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun apkUri(context: Context, apk: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
}
