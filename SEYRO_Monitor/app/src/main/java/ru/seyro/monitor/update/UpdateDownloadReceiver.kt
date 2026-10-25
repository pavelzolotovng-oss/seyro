package ru.seyro.monitor.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

object UpdateDownloader {
    private const val PREFS = "update_download"
    private const val ID = "id"
    private const val SHA = "sha256"

    fun start(context: Context, update: UpdateInfo) {
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("SEYRO Monitor ${update.versionName}")
            .setDescription("Загрузка обновления")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, "updates", "seyro-monitor-${update.versionName}.apk")
        val id = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(ID, id).putString(SHA, update.sha256).apply()
    }

    internal fun expectedDownload(context: Context, id: Long): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(SHA, null)?.takeIf { prefs.getLong(ID, -1L) == id }
    }

    internal fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val expectedSha = UpdateDownloader.expectedDownload(context, id) ?: return
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val source = manager.getUriForDownloadedFile(id) ?: return
        val target = File(context.cacheDir, "updates/seyro-monitor.apk").apply { parentFile?.mkdirs() }
        context.contentResolver.openInputStream(source)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } } ?: return
        if (sha256(target) != expectedSha) {
            target.delete()
            UpdateDownloader.clear(context)
            return
        }
        UpdateDownloader.clear(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", target)
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(install)
        } else {
            context.startActivity(Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
