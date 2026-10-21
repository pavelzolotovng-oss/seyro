package com.seyro.lite

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import libXray.DialerController
import libXray.LibXray
import org.json.JSONObject
import java.util.concurrent.Executors

/** SEYRO VPN runtime backed by official XTLS/libXray (Xray-core). */
class SeyroVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.seyro.lite.START_VPN"
        const val ACTION_STOP = "com.seyro.lite.STOP_VPN"
        const val EXTRA_CONFIG = "xray_config"
        const val EXTRA_TRANSPORT = "transport"
        const val EXTRA_BY_AUTO = "by_auto"
        const val ACTION_STATE_CHANGED = "com.seyro.lite.VPN_STATE_CHANGED"
        @JvmField @Volatile var vpnState: String = "disconnected"
        @JvmField @Volatile var lastError: String = ""
        @JvmField @Volatile var lastProbeDelayMs: Long = -1L
        private const val TAG = "SEYRO-XRAY"
        private const val CHANNEL_ID = "seyro_vpn"
        private const val NOTIFICATION_ID = 1
        @Volatile private var activeInstance: SeyroVpnService? = null
        @JvmStatic fun requestImmediateProbe(reason: String) { Log.d(TAG, "Probe deferred to Xray runtime: " + reason) }
    }

    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var tunDescriptor: ParcelFileDescriptor? = null
    @Volatile private var runtimeStarted = false
    @Volatile private var runtimeStopped = true

    private val dialerController = object : DialerController {
        override fun protectFd(fd: Long): Boolean = this@SeyroVpnService.protect(fd.toInt())
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        DebugLog.i(this, "SERVICE", "Xray service created; Android=" + Build.VERSION.RELEASE + "; SDK=" + Build.VERSION.SDK_INT)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) {
            worker.execute { stopRuntime(startId) }
            return START_NOT_STICKY
        }
        if (intent.action != ACTION_START) return START_NOT_STICKY
        if (vpnState == "connecting" || vpnState == "connected") return START_NOT_STICKY
        val sourceConfig = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
        if (sourceConfig.isBlank()) {
            setState("error", "Профиль пустой. Добавьте ссылку подписки.")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // Mark this runtime as active before any asynchronous startup/cleanup can race.
        runtimeStopped = false
        try { startForegroundNotification() } catch (e: Exception) {
            setState("error", "Не удалось запустить VPN-службу: " + (e.message ?: e.javaClass.simpleName))
            stopSelf(startId)
            return START_NOT_STICKY
        }
        setState("connecting", "")
        worker.execute {
            try {
                val config = JSONObject(sourceConfig)
                require((config.optJSONArray("outbounds")?.length() ?: 0) > 0) { "В конфигурации Xray нет outbounds" }
                val builder = Builder().setSession("SEYRO Xray").setMtu(1500)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
                builder.addAddress("172.19.0.1", 30)
                builder.addDnsServer("1.1.1.1")
                builder.addRoute("0.0.0.0", 0)
                // Keep the app inside the VPN so its health probe uses the same tunnel as user traffic.

                val pfd = builder.establish() ?: error("Android не создал TUN-интерфейс")
                tunDescriptor?.close()
                tunDescriptor = pfd

                LibXray.registerDialerController(dialerController)
                LibXray.setDNS(dialerController, "1.1.1.1:53")
                // Xray-core reads this as an environment variable named xray.tun.fd
                // (or XRAY_TUN_FD), whose value must be the numeric Android TUN fd.
                val env = config.optJSONObject("env") ?: JSONObject().also { config.put("env", it) }
                env.put("xray.tun.fd", pfd.fd.toString())
                val request = JSONObject().put("apiVersion", 3).put("method", "runXray")
                    .put("payload", JSONObject().put("xrayJson", config.toString())).toString()
                val response = JSONObject(LibXray.invoke(request))
                if (!response.optBoolean("success", false)) error(response.optString("error", "libXray rejected config"))
                runtimeStarted = true
                val state = JSONObject(LibXray.invoke(JSONObject().put("apiVersion", 3).put("method", "getXrayState").toString()))
                if (!state.optBoolean("success", false) || state.optJSONObject("data")?.optBoolean("running", false) != true) {
                    error("Xray-core did not enter running state")
                }
                DebugLog.i(this, "XRAY", "Xray-core started; tunFd=" + pfd.fd)
                setState("connected", "")
            } catch (e: Exception) {
                DebugLog.exception(this, "CRASH", "Xray-core startup failed", e)
                Log.e(TAG, "Xray-core startup failed", e)
                stopXrayCore()
                runCatching { tunDescriptor?.close() }
                tunDescriptor = null
                setState("error", "Xray не запустился: " + (e.message ?: e.javaClass.simpleName))
                stopForegroundCompat()
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun stopXrayCore() {
        if (runtimeStarted) runCatching {
            LibXray.invoke(JSONObject().put("apiVersion", 3).put("method", "stopXray").toString())
        }.onFailure { Log.w(TAG, "stopXray failed", it) }
        runtimeStarted = false
        runCatching { LibXray.resetDNS() }
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "SEYRO VPN", NotificationManager.IMPORTANCE_LOW))
        }
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("SEYRO").setContentText("Защищённое соединение Xray-core")
                .setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setContentIntent(pending).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("SEYRO").setContentText("Защищённое соединение Xray-core")
                .setOngoing(true).setContentIntent(pending).build()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        else startForeground(NOTIFICATION_ID, notification)
    }

    @Synchronized
    private fun stopRuntime(startId: Int = 0) {
        if (runtimeStopped) {
            DebugLog.d(this, "STATE", "stopRuntime skipped; runtime already stopped")
            if (startId != 0) stopSelf(startId)
            return
        }
        // Set the guard before broadcasting state or calling into Xray: onDestroy
        // may request another stop while cleanup is still in progress.
        runtimeStopped = true
        setState("disconnecting", "")
        stopXrayCore()
        runCatching { tunDescriptor?.close() }
        tunDescriptor = null
        setState("disconnected", "")
        stopForegroundCompat()
        if (startId != 0) stopSelf(startId) else stopSelf()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
    }

    private fun setState(state: String, error: String) {
        vpnState = state
        lastError = error
        if (error.isNotBlank()) DebugLog.e(this, "STATE", "VPN state=" + state + "; error=" + error)
        else DebugLog.i(this, "STATE", "VPN state=" + state)
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    override fun onRevoke() { worker.execute { stopRuntime() }; super.onRevoke() }
    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        worker.execute { stopRuntime() }
        worker.shutdown()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}